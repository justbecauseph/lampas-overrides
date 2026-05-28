package town.lampas.overrides;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

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

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        String uuid = player.getUUID().toString();
        String username = player.getName().getString();
        LOGGER.info("Player logged in: {} ({})", username, uuid);
        sendWebhookAsync(uuid, username, "login");
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        String uuid = player.getUUID().toString();
        String username = player.getName().getString();
        LOGGER.info("Player logged out: {} ({})", username, uuid);
        sendWebhookAsync(uuid, username, "logout");
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
        LOGGER.info("Payload for player {} event: {}", username, json);

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
}
