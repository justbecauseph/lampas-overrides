package town.lampas.overrides;

import io.github.lightman314.lightmanscurrency.common.bank.BankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import com.mojang.authlib.GameProfile;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

public class BankSyncHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static void handleBankBalanceChange(UUID playerUUID, BankAccount bankAccount, MoneyValue amount, String eventType) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        String username = "Unknown";
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
            if (player != null) {
                username = player.getName().getString();
            } else {
                GameProfile profile = server.getProfileCache().get(playerUUID).orElse(null);
                if (profile != null) {
                    username = profile.getName();
                }
            }
        }

        // Fallback to bank account owner name if still unknown
        if ("Unknown".equals(username)) {
            username = bankAccount.getOwnersName();
        }

        String balance = bankAccount.getStoredMoney().getString();
        String amountStr = amount.getString();

        LOGGER.info("Bank account event: Player {} ({}) did a {} of {} (New Balance: {})",
                username, playerUUID, eventType, amountStr, balance);

        sendWebhookAsync(playerUUID.toString(), username, eventType, amountStr, balance);
    }

    private static void sendWebhookAsync(String uuid, String username, String eventType, String amount, String balance) {
        String webhookUrl = ModConfig.BANK_SYNC_URL.get();
        String apiKey = ModConfig.API_KEY.get();

        if (webhookUrl == null || webhookUrl.isBlank()) {
            LOGGER.warn("Bank Sync Webhook URL is not configured. Skipping sync.");
            return;
        }

        // Using Gson to build the JSON object safely.
        com.google.gson.JsonObject jsonObject = new com.google.gson.JsonObject();
        jsonObject.addProperty("uuid", uuid);
        jsonObject.addProperty("username", username);
        jsonObject.addProperty("event", eventType);
        jsonObject.addProperty("amount", amount);
        jsonObject.addProperty("balance", balance);

        String json = jsonObject.toString();

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
                            LOGGER.info("Successfully synced player {} bank event: {}", username, eventType);
                        } else {
                            LOGGER.error("Failed to sync bank event for {}. Status code: {}, Response: {}",
                                    username, response.statusCode(), response.body());
                        }
                    })
                    .exceptionally(ex -> {
                        LOGGER.error("Error sending bank webhook for player {}: {}", username, ex.getMessage(), ex);
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Failed to build HTTP request for bank event: {}", e.getMessage(), e);
        }
    }
}
