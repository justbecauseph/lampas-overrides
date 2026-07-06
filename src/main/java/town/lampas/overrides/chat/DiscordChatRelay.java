package town.lampas.overrides.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import town.lampas.overrides.HttpUtil;
import town.lampas.overrides.ModConfig;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Echoes in-game global chat to a Discord webhook.
 *
 * <p>Performance/safety contract, mirroring {@link town.lampas.overrides.audit.BlockAuditStore}:
 * the server thread only ever does a non-blocking {@link ArrayBlockingQueue#offer} via the
 * {@code onXxx} producer methods. A single dedicated daemon thread owns all HTTP I/O, batching,
 * and rate limiting, so a slow or rate-limited Discord can never stall the tick loop.
 *
 * <p>Discord safeguards (so the webhook is never flagged/banned):
 * <ul>
 *   <li>Buffered batching: messages collected over a flush window are coalesced into as few
 *       requests as possible (consecutive same-identity lines merged up to 2000 chars / N lines).</li>
 *   <li>Self-throttling: a minimum interval between requests caps the rate well under Discord's
 *       limits, and the sender always honors {@code 429} {@code retry_after} plus the
 *       {@code X-RateLimit-*} headers, backing off rather than hammering.</li>
 *   <li>Mention safety: {@code allowed_mentions.parse = []} so relayed text can never ping
 *       {@code @everyone}/roles/users; {@code @everyone}/{@code @here} are also neutralized in text.</li>
 *   <li>Payload hygiene: usernames sanitized to Discord's webhook rules, content stripped of legacy
 *       color codes and clamped to 2000 chars, bounded queue with drop-oldest backpressure.</li>
 * </ul>
 */
public final class DiscordChatRelay {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile DiscordChatRelay INSTANCE;

    private static final int MAX_DRAIN = 500;
    private static final int MAX_429_RETRIES = 5;
    private static final int CONTENT_LIMIT = 2000;
    private static final int USERNAME_LIMIT = 80;
    /** Zero-width space, used to defuse ping tokens without visibly changing the text. */
    private static final String ZWSP = "​";
    /** Legacy section-sign color/format codes (e.g. §c), stripped from relayed text. */
    private static final String SECTION_CODE = "§.";

    private final String webhookUrl;
    private final String serverName;
    private final String serverAvatar;
    private final String avatarTemplate;
    private final long flushIntervalMs;
    private final long minSendIntervalMs;
    private final int maxLines;

    private final ArrayBlockingQueue<RelayMessage> queue;
    private final Thread sender;
    private volatile boolean running = true;
    private final AtomicLong dropped = new AtomicLong();
    private volatile long lastDropLog = 0;

    /** Earliest wall-clock time the next request may be sent (throttle + rate-limit reset). */
    private long nextAllowedSend = 0;

    private DiscordChatRelay(String webhookUrl, String serverName, String serverAvatar, String avatarTemplate,
                             long flushIntervalMs, long minSendIntervalMs, int maxLines, int maxQueue) {
        this.webhookUrl = webhookUrl;
        this.serverName = sanitizeUsername(serverName);
        this.serverAvatar = serverAvatar == null || serverAvatar.isBlank() ? null : serverAvatar;
        this.avatarTemplate = avatarTemplate;
        this.flushIntervalMs = flushIntervalMs;
        this.minSendIntervalMs = minSendIntervalMs;
        this.maxLines = maxLines;
        this.queue = new ArrayBlockingQueue<>(maxQueue);
        this.sender = new Thread(this::runSender, "lampas-discord-relay");
        this.sender.setDaemon(true);
    }

    // --- lifecycle ---------------------------------------------------------

    public static void init() {
        if (INSTANCE != null) {
            return;
        }
        if (!ModConfig.DISCORD_RELAY_ENABLED.get()) {
            return;
        }
        String url = ModConfig.DISCORD_RELAY_WEBHOOK_URL.get();
        if (url == null || url.isBlank() || !isAcceptableUrl(url)) {
            LOGGER.warn("Discord chat relay is enabled but the webhook URL is missing or not https; relay disabled.");
            return;
        }
        DiscordChatRelay relay = new DiscordChatRelay(
                url.trim(),
                ModConfig.DISCORD_RELAY_SERVER_NAME.get(),
                ModConfig.DISCORD_RELAY_SERVER_AVATAR.get(),
                ModConfig.DISCORD_RELAY_AVATAR_TEMPLATE.get(),
                ModConfig.DISCORD_RELAY_FLUSH_MS.get(),
                ModConfig.DISCORD_RELAY_MIN_SEND_INTERVAL_MS.get(),
                ModConfig.DISCORD_RELAY_MAX_LINES.get(),
                ModConfig.DISCORD_RELAY_MAX_QUEUE.get());
        INSTANCE = relay;
        relay.sender.start();
        LOGGER.info("Discord chat relay started.");
    }

    public static DiscordChatRelay get() {
        return INSTANCE;
    }

    /** Discord webhooks are always https; loopback http is allowed for a local relay proxy. */
    private static boolean isAcceptableUrl(String url) {
        return url.startsWith("https://")
                || url.startsWith("http://localhost")
                || url.startsWith("http://127.0.0.1");
    }

    public static void shutdown() {
        DiscordChatRelay relay = INSTANCE;
        INSTANCE = null;
        if (relay != null) {
            relay.stop();
        }
    }

    private void stop() {
        running = false;
        sender.interrupt();
        try {
            sender.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // --- producer side (server thread) -------------------------------------

    /** Relay normal player chat under the player's display name + head avatar. */
    public void onPlayerChat(String displayName, UUID uuid, String content) {
        if (!ModConfig.DISCORD_RELAY_PLAYER_CHAT.get()) {
            return;
        }
        String avatar = avatarTemplate == null ? null : avatarTemplate
                .replace("{uuid}", uuid == null ? "" : uuid.toString())
                .replace("{name}", displayName == null ? "" : displayName);
        enqueue(new RelayMessage(displayName, avatar, content));
    }

    /** Relay a server/system line (say, me, tellraw, joins/leaves/deaths/advancements). */
    public void onServerMessage(String content) {
        enqueue(new RelayMessage(serverName, serverAvatar, content));
    }

    private void enqueue(RelayMessage msg) {
        // Offline mode: drop relayed messages outright instead of queuing sends that can't go out.
        if (HttpUtil.skipHttpCall("discord relay")) {
            return;
        }
        String content = sanitizeContent(msg.content());
        if (content.isEmpty()) {
            return;
        }
        RelayMessage clean = new RelayMessage(sanitizeUsername(msg.username()), msg.avatarUrl(), content);
        // Drop-oldest backpressure: under chat spam we'd rather keep the newest lines than block.
        while (!queue.offer(clean)) {
            if (queue.poll() != null) {
                long n = dropped.incrementAndGet();
                long now = System.currentTimeMillis();
                if (now - lastDropLog > 10000) {
                    lastDropLog = now;
                    LOGGER.warn("Discord relay queue full; dropped {} messages so far. Raise discordRelayMaxQueue or lower flush interval.", n);
                }
            }
        }
    }

    // --- consumer side (sender thread) -------------------------------------

    private void runSender() {
        List<RelayMessage> batch = new ArrayList<>();
        while (running || !queue.isEmpty()) {
            try {
                RelayMessage first = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.clear();
                batch.add(first);
                queue.drainTo(batch, MAX_DRAIN - 1);
                for (Payload payload : coalesce(batch)) {
                    dispatch(payload);
                }
            } catch (InterruptedException e) {
                // Shutdown signal; loop re-checks `running` and drains anything left.
                if (!running) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                LOGGER.error("Discord relay sender error: {}", e.getMessage(), e);
            }
        }
    }

    /** Merges consecutive same-identity messages into the fewest webhook payloads. */
    private List<Payload> coalesce(List<RelayMessage> batch) {
        List<Payload> out = new ArrayList<>();
        String curName = null;
        String curAvatar = null;
        StringBuilder buf = new StringBuilder();
        int lines = 0;
        for (RelayMessage m : batch) {
            boolean sameId = curName != null
                    && curName.equals(m.username())
                    && Objects.equals(curAvatar, m.avatarUrl());
            boolean fits = buf.length() + 1 + m.content().length() <= CONTENT_LIMIT && lines < maxLines;
            if (sameId && fits) {
                buf.append('\n').append(m.content());
                lines++;
            } else {
                if (curName != null) {
                    out.add(new Payload(curName, curAvatar, buf.toString()));
                }
                curName = m.username();
                curAvatar = m.avatarUrl();
                buf = new StringBuilder(m.content());
                lines = 1;
            }
        }
        if (curName != null) {
            out.add(new Payload(curName, curAvatar, buf.toString()));
        }
        return out;
    }

    private void dispatch(Payload payload) throws InterruptedException {
        throttle();
        String body = buildJson(payload);
        for (int attempt = 0; attempt < MAX_429_RETRIES; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> resp = HttpUtil.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                int sc = resp.statusCode();
                if (sc == 429) {
                    long backoff = retryAfterMillis(resp, 1000L * (attempt + 1));
                    LOGGER.warn("Discord webhook rate limited (429); backing off {}ms.", backoff);
                    nextAllowedSend = System.currentTimeMillis() + backoff;
                    Thread.sleep(backoff);
                    continue;
                }
                if (sc >= 200 && sc < 300) {
                    applyRateLimitHeaders(resp);
                    return;
                }
                // 4xx (bad payload) / 5xx: log once and drop -- never spin retrying a poison message.
                LOGGER.error("Discord webhook returned {}: {}", sc, truncate(resp.body(), 300));
                return;
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                long backoff = 1000L * (attempt + 1);
                LOGGER.warn("Discord webhook send failed ({}); retrying in {}ms.", e.getMessage(), backoff);
                Thread.sleep(backoff);
            }
        }
        LOGGER.error("Discord webhook gave up after {} attempts; dropping a message.", MAX_429_RETRIES);
    }

    /** Blocks until {@link #nextAllowedSend} and the minimum inter-request spacing have elapsed. */
    private void throttle() throws InterruptedException {
        long now = System.currentTimeMillis();
        long wait = nextAllowedSend - now;
        if (wait > 0) {
            Thread.sleep(wait);
        }
        nextAllowedSend = System.currentTimeMillis() + minSendIntervalMs;
    }

    /** Proactively pause until the bucket resets when Discord reports it is exhausted. */
    private void applyRateLimitHeaders(HttpResponse<?> resp) {
        resp.headers().firstValue("X-RateLimit-Remaining").ifPresent(remaining -> {
            if ("0".equals(remaining.trim())) {
                resp.headers().firstValue("X-RateLimit-Reset-After").ifPresent(reset -> {
                    try {
                        long ms = (long) (Double.parseDouble(reset.trim()) * 1000) + 100;
                        nextAllowedSend = Math.max(nextAllowedSend, System.currentTimeMillis() + ms);
                    } catch (NumberFormatException ignored) {
                        // header malformed; the min-interval throttle still applies
                    }
                });
            }
        });
    }

    private static long retryAfterMillis(HttpResponse<String> resp, long fallback) {
        // Discord puts the authoritative value in the JSON body ("retry_after", seconds);
        // the header is a coarse integer fallback.
        try {
            JsonObject obj = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
            if (obj.has("retry_after")) {
                return (long) (obj.get("retry_after").getAsDouble() * 1000) + 250;
            }
        } catch (Exception ignored) {
            // fall through to header / fallback
        }
        return resp.headers().firstValue("Retry-After")
                .map(h -> {
                    try {
                        return (long) (Double.parseDouble(h.trim()) * 1000) + 250;
                    } catch (NumberFormatException e) {
                        return fallback;
                    }
                })
                .orElse(fallback);
    }

    // --- payload building / sanitization -----------------------------------

    private String buildJson(Payload payload) {
        JsonObject json = new JsonObject();
        json.addProperty("content", payload.content());
        json.addProperty("username", payload.username());
        if (payload.avatarUrl() != null && !payload.avatarUrl().isBlank()) {
            json.addProperty("avatar_url", payload.avatarUrl());
        }
        JsonObject allowed = new JsonObject();
        allowed.add("parse", new JsonArray()); // suppress all pings, no matter the text
        json.add("allowed_mentions", allowed);
        return json.toString();
    }

    /** Strips legacy section color codes, neutralizes mass-ping tokens, and clamps to Discord's limit. */
    private static String sanitizeContent(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replaceAll(SECTION_CODE, "").strip();
        // Defense in depth beyond allowed_mentions: break the literal tokens with a zero-width space.
        s = s.replace("@everyone", "@" + ZWSP + "everyone").replace("@here", "@" + ZWSP + "here");
        if (s.length() > CONTENT_LIMIT) {
            s = s.substring(0, CONTENT_LIMIT - 1) + "…";
        }
        return s;
    }

    /**
     * Coerces a name into Discord's webhook-username rules: 1..80 chars, no {@code @ # : `},
     * cannot contain "discord", and not "everyone"/"here".
     */
    private static String sanitizeUsername(String raw) {
        String s = raw == null ? "" : raw.replaceAll(SECTION_CODE, "").strip();
        s = s.replace("@", "").replace("#", "").replace(":", "").replace("`", "");
        s = s.replaceAll("(?i)discord", "disc" + ZWSP + "ord");
        if (s.equalsIgnoreCase("everyone") || s.equalsIgnoreCase("here")) {
            s = "_" + s;
        }
        if (s.length() > USERNAME_LIMIT) {
            s = s.substring(0, USERNAME_LIMIT);
        }
        s = s.strip();
        return s.isEmpty() ? "Player" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private record Payload(String username, String avatarUrl, String content) {}
}
