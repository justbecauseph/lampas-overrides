package town.lampas.overrides;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side cache of players' pronouns, fetched by Minecraft UUID from the public PronounDB v2
 * lookup endpoint. Drives the {@code [he/him]} display-name prefix in {@link PronounStatusHandler}.
 *
 * <p>Mirrors the async + UUID-keyed TTL caching used by {@link SocialCache} / {@link BountyApiFetcher}.
 * Pronouns change rarely, so the TTL is generous and entries are warmed once on join.
 */
public final class PronounCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long TTL_MS = 30 * 60 * 1000L;

    // PronounDB asks callers to send an identifying User-Agent (app name, version, link).
    private static final String USER_AGENT =
            "lampas-overrides/" + LampasOverridesMod.MODID + " (+https://portal.lampas.town)";

    // Empty-string sentinel = "looked up, has no pronouns" — distinct from null = "never looked up".
    private static final String NONE = "";

    private static final Map<UUID, String> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_FETCH = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private PronounCache() {}

    /** Cached pronoun pair for the player (e.g. {@code "he/him"}), or {@code null} if unknown/none. */
    public static String get(UUID uuid) {
        if (uuid == null) return null;
        String s = CACHE.get(uuid);
        return (s == null || s.isEmpty()) ? null : s;
    }

    /** Drops the cached entry for a player (call on logout to keep the maps bounded). */
    public static void forget(UUID uuid) {
        if (uuid == null) return;
        CACHE.remove(uuid);
        LAST_FETCH.remove(uuid);
    }

    /**
     * Warms the cache for a single player, deduped by TTL and an in-flight guard. When the fetched
     * value differs from what was cached, {@code onUpdate} runs (on the HTTP thread — callers that
     * touch game state should hop back to the server thread themselves).
     */
    public static void prefetch(UUID uuid, Runnable onUpdate) {
        if (!ModConfig.SHOW_PRONOUNS.get()) return;
        if (uuid == null) return;

        String url = ModConfig.PRONOUNDB_API_URL.get();
        if (url == null || url.isEmpty()) return;
        // Offline mode: no PronounDB lookup; names just show without a pronoun tag.
        if (HttpUtil.skipHttpCall("pronoun fetch")) return;

        long now = System.currentTimeMillis();
        if (now - LAST_FETCH.getOrDefault(uuid, 0L) <= TTL_MS) return;
        if (!IN_FLIGHT.add(uuid)) return;
        LAST_FETCH.put(uuid, now);

        // PronounDB requires dashed UUIDs; undashed ids are silently dropped from the response.
        String id = uuid.toString().toLowerCase(java.util.Locale.ROOT);
        String fullUrl = url + (url.contains("?") ? "&" : "?") + "platform=minecraft&ids=" + id;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpUtil.HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        try {
                            String resolved = NONE;
                            if (response.statusCode() == 200) {
                                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                                if (json.has(id) && json.get(id).isJsonObject()) {
                                    String pair = resolvePronouns(json.getAsJsonObject(id));
                                    if (pair != null) resolved = pair;
                                }
                            }
                            String prev = CACHE.put(uuid, resolved);
                            if (onUpdate != null && !Objects.equals(prev, resolved)) {
                                onUpdate.run();
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Failed to parse PronounDB response for {}: {}", id, e.getMessage());
                        } finally {
                            IN_FLIGHT.remove(uuid);
                        }
                    })
                    .exceptionally(ex -> {
                        LOGGER.warn("Failed to fetch pronouns from {}: {}", fullUrl, ex.getMessage());
                        IN_FLIGHT.remove(uuid);
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Failed to build or send pronouns request: {}", e.getMessage(), e);
            IN_FLIGHT.remove(uuid);
        }
    }

    /**
     * Resolves a PronounDB entry's {@code sets} into a canonical display pair, using only the first
     * set code (so {@code ["she","they"]} becomes {@code "she/her"}, not {@code "she/they"}).
     * Prefers the {@code en} locale, falling back to whichever locale appears first.
     */
    private static String resolvePronouns(JsonObject entry) {
        if (!entry.has("sets") || !entry.get("sets").isJsonObject()) return null;
        JsonObject sets = entry.getAsJsonObject("sets");

        JsonArray codes = null;
        if (sets.has("en") && sets.get("en").isJsonArray()) {
            codes = sets.getAsJsonArray("en");
        } else {
            for (Map.Entry<String, com.google.gson.JsonElement> e : sets.entrySet()) {
                if (e.getValue().isJsonArray()) { codes = e.getValue().getAsJsonArray(); break; }
            }
        }
        if (codes == null || codes.isEmpty()) return null;
        return canonicalPair(codes.get(0).getAsString());
    }

    /** Maps a PronounDB set code to its canonical English display form. */
    private static String canonicalPair(String code) {
        return switch (code) {
            case "he" -> "he/him";
            case "she" -> "she/her";
            case "they" -> "they/them";
            case "it" -> "it/its";
            case "any" -> "any";
            case "ask" -> "ask";
            case "other" -> "other";
            // "avoid" means "use my name, not pronouns" — nothing meaningful to prepend.
            default -> null;
        };
    }
}
