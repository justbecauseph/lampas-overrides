package town.lampas.overrides;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class PlayerActivityListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final java.util.Map<java.util.UUID, String> PLAYER_FACTIONS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Long> LAST_FETCH_TIME = new java.util.concurrent.ConcurrentHashMap<>();

    public static String getPlayerFaction(java.util.UUID uuid) {
        long now = System.currentTimeMillis();
        long lastFetch = LAST_FETCH_TIME.getOrDefault(uuid, 0L);
        // Refresh in background if missing or fetched more than 30 seconds ago
        if (now - lastFetch > 30000L) {
            LAST_FETCH_TIME.put(uuid, now);
            fetchPlayerFactionAsync(uuid);
        }
        return PLAYER_FACTIONS.getOrDefault(uuid, "");
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        String uuid = player.getUUID().toString();
        String username = player.getName().getString();
        LOGGER.info("Player logged in: {} ({})", username, uuid);
        sendWebhookAsync(uuid, username, "login");
        // Fetch faction immediately on login
        fetchPlayerFactionAsync(player.getUUID());
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        String uuid = player.getUUID().toString();
        String username = player.getName().getString();
        LOGGER.info("Player logged out: {} ({})", username, uuid);
        sendWebhookAsync(uuid, username, "logout");
        // Clean up maps
        PLAYER_FACTIONS.remove(player.getUUID());
        LAST_FETCH_TIME.remove(player.getUUID());
    }

    private void sendWebhookAsync(String uuid, String username, String eventType) {
        String webhookUrl = ModConfig.WEBHOOK_URL.get();
        String apiKey = ModConfig.API_KEY.get();

        if (webhookUrl == null || webhookUrl.isBlank()) {
            LOGGER.warn("Webhook URL is not configured. Skipping event sync.");
            return;
        }

        // Create the JSON payload manually to keep the mod zero-dependency.
        // UUID is alphanumeric with hyphens, and username is alphanumeric with underscores.
        // Thus, formatting is completely safe and doesn't require complex escaping.
        String json = String.format("{\"uuid\":\"%s\",\"username\":\"%s\",\"event\":\"%s\"}", uuid, username, eventType);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            LOGGER.info("Successfully synced player {} event: {}", username, eventType);
                        } else {
                            LOGGER.error("Failed to sync player event for {}. Status code: {}, Response: {}",
                                    username, response.statusCode(), response.body());
                        }
                    })
                    .exceptionally(ex -> {
                        LOGGER.error("Error sending webhook for player {}: {}", username, ex.getMessage(), ex);
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Failed to build HTTP request for player event: {}", e.getMessage(), e);
        }
    }

    @SubscribeEvent
    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) {
            return;
        }

        BlockState state = event.getLevel().getBlockState(event.getPos());
        Player player = event.getEntity();
        if (player != null && !player.hasPermissions(2)) {
            if (isRestrictedBlock(state)) {
                String role = getPlayerFaction(player.getUUID());
                
                boolean isFurnace = state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE);
                if (!isFurnace) {
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (id != null) {
                        String path = id.getPath().toLowerCase();
                        if (path.contains("furnace") && !path.contains("minecart")) {
                            isFurnace = true;
                        }
                    }
                }
                
                boolean blockInteraction = false;
                String requiredRole = "";
                
                if (isFurnace) {
                    requiredRole = "LMI";
                    if (!requiredRole.equalsIgnoreCase(role)) {
                        blockInteraction = true;
                    }
                } else if (state.is(Blocks.BREWING_STAND)) {
                    requiredRole = "FISHERIES";
                    if (!requiredRole.equalsIgnoreCase(role)) {
                        blockInteraction = true;
                    }
                } else {
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (id != null && id.getNamespace().equals("lightmanscurrency") && id.getPath().equals("tax_block")) {
                        requiredRole = "NOBILITY";
                        if (!requiredRole.equalsIgnoreCase(role)) {
                            blockInteraction = true;
                        }
                    }
                }
                
                if (blockInteraction) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§cYou must have the " + requiredRole + " role to interact with this block!"
                    ));
                }
            }
        }
    }

    private boolean isRestrictedBlock(BlockState state) {
        if (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.BREWING_STAND)) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id != null) {
            String path = id.getPath().toLowerCase();
            if (path.contains("furnace") && !path.contains("minecart")) {
                return true;
            }
            if (id.getNamespace().equals("lightmanscurrency") && id.getPath().equals("tax_block")) {
                return true;
            }
        }
        return false;
    }

    public static void fetchPlayerFactionAsync(java.util.UUID uuid) {
        String url = getPlayerApiUrl(uuid.toString());
        String apiKey = ModConfig.API_KEY.get();

        if (url == null || url.isBlank()) {
            LOGGER.warn("API URL is not configured. Skipping player faction fetch.");
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("x-api-key", apiKey)
                    .GET()
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
                                if (json.has("faction") && !json.get("faction").isJsonNull()) {
                                    String faction = json.get("faction").getAsString();
                                    PLAYER_FACTIONS.put(uuid, faction);
                                    LOGGER.info("Fetched faction for player UUID {}: {}", uuid, faction);
                                } else {
                                    PLAYER_FACTIONS.put(uuid, "");
                                    LOGGER.info("Player UUID {} has no faction.", uuid);
                                }
                            } catch (Exception ex) {
                                LOGGER.error("Failed to parse player API response: {}", ex.getMessage(), ex);
                            }
                        } else {
                            LOGGER.error("Failed to fetch player faction. Status code: {}, Response: {}",
                                    response.statusCode(), response.body());
                        }
                    })
                    .exceptionally(ex -> {
                        LOGGER.error("Error fetching player faction: {}", ex.getMessage(), ex);
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Failed to build player faction HTTP request: {}", e.getMessage(), e);
        }
    }

    private static String getPlayerApiUrl(String playerUuid) {
        String playerApiUrl = ModConfig.PLAYER_API_URL.get();
        if (playerApiUrl == null || playerApiUrl.isBlank()) {
            return null;
        }
        return playerApiUrl + "?uuid=" + playerUuid;
    }
}
