package town.lampas.overrides;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class BountyApiFetcher {
    private static final Logger LOGGER = LogUtils.getLogger();

    public record Bounty(UUID id, String title, String description, String prizeType, int prizeAmount, String prizeItem, String prizeOther, String poster, String faction, java.util.Set<String> claims) {
        public boolean isClaimedBy(UUID playerUuid) {
            return isClaimedBy(normalizeUuid(playerUuid));
        }

        /** Friendly faction label for display, mirroring the web portal's mapping. Returns null when factionless. */
        public String getFactionDisplayName() {
            if (faction == null || faction.isEmpty()) {
                return null;
            }
            return switch (faction) {
                case "LMI" -> "LMI";
                case "MERCHANTS" -> "Merchants";
                case "NAVY" -> "Royal Navy";
                case "RELIGION" -> "The Faith";
                case "TOURISM" -> "Tourism";
                case "NOBILITY" -> "Nobility";
                case "FISHERIES" -> "Fisheries";
                case "INTERNAL_AFFAIRS", "INFRASTRUCTURE" -> "Government";
                default -> faction.charAt(0) + faction.substring(1).toLowerCase().replace("_", " ");
            };
        }

        /** Variant taking a pre-normalized UUID string, to avoid recomputing it in tight loops. */
        public boolean isClaimedBy(String normalizedUuid) {
            return claims.contains(normalizedUuid);
        }

        public String getRewardText() {
            if (prizeAmount == 0 || prizeType == null || "NONE".equalsIgnoreCase(prizeType)) {
                return "NONE";
            }
            if ("ITEM".equalsIgnoreCase(prizeType)) {
                return prizeItem != null ? prizeItem : "Unknown Item";
            } else if ("OTHER".equalsIgnoreCase(prizeType)) {
                return prizeOther != null ? prizeOther : "Unknown Reward";
            } else if ("MONEY".equalsIgnoreCase(prizeType) || "COINS".equalsIgnoreCase(prizeType)) {
                return prizeAmount == 1 ? "1 Aur" : prizeAmount + " Aurs";
            } else {
                return prizeAmount + " " + prizeType;
            }
        }
    }

    /** Lowercase, dash-stripped form matching how claim UUIDs are stored. */
    public static String normalizeUuid(UUID uuid) {
        return uuid.toString().toLowerCase().replace("-", "");
    }

    private static volatile List<Bounty> cachedBounties = java.util.Collections.emptyList();
    private static volatile long lastFetchTime = 0;

    public static String getBountiesUrl() {
        return ModConfig.BOUNTIES_API_URL.get();
    }

    public interface BountiesReceiver {
        void accept(List<Bounty> bounties);
    }

    public static List<Bounty> getBounties() {
        long now = System.currentTimeMillis();
        if (now - lastFetchTime > 15000L) {
            lastFetchTime = now;
            fetchBountiesAsync(null);
        }
        return cachedBounties;
    }

    public static void getBountiesAsync(BountiesReceiver receiver) {
        long now = System.currentTimeMillis();
        if (now - lastFetchTime > 15000L) {
            lastFetchTime = now;
            fetchBountiesAsync(receiver);
        } else {
            if (receiver != null) {
                receiver.accept(cachedBounties);
            }
        }
    }

    public static void forceRefresh() {
        lastFetchTime = System.currentTimeMillis();
        fetchBountiesAsync(null);
    }

    private static void fetchBountiesAsync(BountiesReceiver receiver) {
        String url = getBountiesUrl();
        String apiKey = ModConfig.API_KEY.get();

        if (url == null || url.isEmpty()) {
            if (receiver != null) receiver.accept(new ArrayList<>());
            return;
        }

        // Offline mode: serve the last cached bounties instead of hitting the portal.
        if (HttpUtil.skipHttpCall("bounties fetch")) {
            if (receiver != null) receiver.accept(cachedBounties);
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("x-api-key", apiKey)
                    .GET()
                    .build();

            HttpUtil.HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        List<Bounty> tempBounties = new ArrayList<>();
                        if (response.statusCode() == 200) {
                            try {
                                JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
                                for (JsonElement elem : jsonArray) {
                                    JsonObject obj = elem.getAsJsonObject();
                                    UUID bountyId = UUID.fromString(obj.get("id").getAsString());
                                    String title = obj.get("title").getAsString();
                                    String description = obj.get("description").getAsString();
                                    String prizeType = obj.get("prizeType").getAsString();

                                    int prizeAmount = 0;
                                    if (obj.has("prizeAmount") && !obj.get("prizeAmount").isJsonNull()) {
                                        prizeAmount = obj.get("prizeAmount").getAsInt();
                                    }

                                    String prizeItem = null;
                                    if (obj.has("prizeItem") && !obj.get("prizeItem").isJsonNull()) {
                                        prizeItem = obj.get("prizeItem").getAsString();
                                    }

                                    String prizeOther = null;
                                    if (obj.has("prizeOther") && !obj.get("prizeOther").isJsonNull()) {
                                        prizeOther = obj.get("prizeOther").getAsString();
                                    }

                                    java.util.Set<String> claimsSet = new java.util.HashSet<>();
                                    if (obj.has("claims") && obj.get("claims").isJsonArray()) {
                                        JsonArray claimsArr = obj.get("claims").getAsJsonArray();
                                        for (JsonElement c : claimsArr) {
                                            if (!c.isJsonNull()) {
                                                claimsSet.add(c.getAsString().toLowerCase().replace("-", ""));
                                            }
                                        }
                                    }

                                    String poster = "Unknown";
                                    if (obj.has("poster") && !obj.get("poster").isJsonNull()) {
                                        poster = obj.get("poster").getAsString();
                                    }

                                    String faction = null;
                                    if (obj.has("faction") && !obj.get("faction").isJsonNull()) {
                                        faction = obj.get("faction").getAsString();
                                    }

                                    tempBounties.add(new Bounty(bountyId, title, description, prizeType, prizeAmount, prizeItem, prizeOther, poster, faction, claimsSet));
                                }
                                cachedBounties = java.util.Collections.unmodifiableList(tempBounties);
                            } catch (Exception e) {
                                LOGGER.error("Failed to parse bounties response: {}", e.getMessage(), e);
                            }
                        }
                        if (receiver != null) {
                            receiver.accept(cachedBounties);
                        }
                    })
                    .exceptionally(ex -> {
                        LOGGER.warn("Failed to fetch bounties from API endpoint {}: {}", url, ex.getMessage());
                        if (receiver != null) {
                            receiver.accept(cachedBounties);
                        }
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Failed to build or send bounties request: {}", e.getMessage(), e);
            if (receiver != null) {
                receiver.accept(cachedBounties);
            }
        }
    }

    public static void claimBountyAsync(ServerPlayer player, UUID bountyId, Runnable onSuccess, java.util.function.Consumer<String> onFailure) {
        String url = getBountiesUrl();
        String apiKey = ModConfig.API_KEY.get();

        if (url == null || url.isEmpty()) {
            onFailure.accept("Bounty API is not configured.");
            return;
        }

        if (HttpUtil.skipHttpCall("bounty claim " + bountyId)) {
            onFailure.accept("Bounties are unavailable right now (server is in offline mode).");
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("uuid", player.getUUID().toString());
        body.addProperty("bountyId", bountyId.toString());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("x-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpUtil.HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            player.server.execute(() -> {
                                forceRefresh();
                                onSuccess.run();
                            });
                        } else {
                            String error = "Failed to claim bounty: HTTP " + response.statusCode();
                            try {
                                JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
                                if (obj.has("error")) {
                                    error = obj.get("error").getAsString();
                                }
                            } catch (Exception e) {}
                            final String finalErr = error;
                            player.server.execute(() -> onFailure.accept(finalErr));
                        }
                    })
                    .exceptionally(ex -> {
                        player.server.execute(() -> onFailure.accept("Error connecting to server: " + ex.getMessage()));
                        return null;
                    });
        } catch (Exception e) {
            onFailure.accept("Failed to build request: " + e.getMessage());
        }
    }
}
