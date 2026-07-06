package town.lampas.overrides;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Client-side cache of players' self-linked YouTube/Twitch channels, fetched in batches
 * from the lampas {@code /api/minecraft/socials} endpoint. Mirrors the async + UUID-keyed
 * TTL caching used by {@link BountyApiFetcher} / {@code PlayerActivityListener}.
 */
public final class SocialCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long TTL_MS = 5 * 60 * 1000L;

    public record Socials(String youtubeUrl, String youtubeHandle, String twitchUrl, String twitchHandle) {
        public boolean hasYoutube() { return youtubeUrl != null && !youtubeUrl.isEmpty(); }
        public boolean hasTwitch() { return twitchUrl != null && !twitchUrl.isEmpty(); }
        public boolean isEmpty() { return !hasYoutube() && !hasTwitch(); }
    }

    private static final Socials EMPTY = new Socials(null, null, null, null);

    private static final Map<UUID, Socials> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_FETCH = new ConcurrentHashMap<>();
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private SocialCache() {}

    /** Returns cached socials for the player (or {@code null} if unknown/empty), scheduling a refresh when stale. */
    public static Socials get(UUID uuid) {
        if (uuid == null) return null;
        long now = System.currentTimeMillis();
        if (now - LAST_FETCH.getOrDefault(uuid, 0L) > TTL_MS) {
            prefetch(Collections.singletonList(uuid));
        }
        Socials s = CACHE.get(uuid);
        return (s == null || s.isEmpty()) ? null : s;
    }

    /**
     * Drops cached entries for players no longer in {@code keep} (e.g. those who logged out),
     * so the maps stay bounded to the current roster over a long-lived client session.
     */
    public static void retainOnly(Collection<UUID> keep) {
        Set<UUID> online = keep instanceof Set<UUID> set ? set : new java.util.HashSet<>(keep);
        CACHE.keySet().retainAll(online);
        LAST_FETCH.keySet().retainAll(online);
        // IN_FLIGHT entries clear themselves when their request completes.
    }

    /** Batch-warms the cache for a set of UUIDs, deduped by TTL and an in-flight guard. */
    public static void prefetch(Collection<UUID> uuids) {
        if (!ModConfig.SHOW_SOCIAL_ICONS.get()) return;

        String url = ModConfig.SOCIALS_API_URL.get();
        if (url == null || url.isEmpty()) return;
        if (HttpUtil.skipHttpCall("socials fetch")) return;
        String apiKey = ModConfig.API_KEY.get();

        long now = System.currentTimeMillis();
        List<UUID> toFetch = new ArrayList<>();
        for (UUID uuid : uuids) {
            if (uuid == null) continue;
            if (now - LAST_FETCH.getOrDefault(uuid, 0L) <= TTL_MS) continue;
            if (!IN_FLIGHT.add(uuid)) continue;
            LAST_FETCH.put(uuid, now);
            toFetch.add(uuid);
        }
        if (toFetch.isEmpty()) return;

        String ids = toFetch.stream()
                .map(u -> u.toString().replace("-", "").toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(","));
        String fullUrl = url + (url.contains("?") ? "&" : "?") + "uuids=" + ids;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("x-api-key", apiKey)
                    .GET()
                    .build();

            HttpUtil.HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        try {
                            if (response.statusCode() == 200) {
                                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                                for (UUID uuid : toFetch) {
                                    String key = uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
                                    if (json.has(key) && json.get(key).isJsonObject()) {
                                        JsonObject o = json.getAsJsonObject(key);
                                        CACHE.put(uuid, new Socials(
                                                str(o, "youtubeUrl"), str(o, "youtubeHandle"),
                                                str(o, "twitchUrl"), str(o, "twitchHandle")));
                                    } else {
                                        // No links for this player; cache the empty result to avoid re-querying every TTL.
                                        CACHE.put(uuid, EMPTY);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Failed to parse socials response: {}", e.getMessage());
                        } finally {
                            toFetch.forEach(IN_FLIGHT::remove);
                        }
                    })
                    .exceptionally(ex -> {
                        LOGGER.warn("Failed to fetch socials from {}: {}", fullUrl, ex.getMessage());
                        toFetch.forEach(IN_FLIGHT::remove);
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Failed to build or send socials request: {}", e.getMessage(), e);
            toFetch.forEach(IN_FLIGHT::remove);
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
