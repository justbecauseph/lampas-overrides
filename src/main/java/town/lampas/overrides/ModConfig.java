package town.lampas.overrides;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ModConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> WEBHOOK_URL;
    public static final ModConfigSpec.ConfigValue<String> API_KEY;

    public static final ModConfigSpec.ConfigValue<String> BANK_SYNC_URL;
    public static final ModConfigSpec.ConfigValue<String> PLAYER_API_URL;

    public static final ModConfigSpec.ConfigValue<Boolean> TAX_COLLECTOR_FORCE_ACCEPTANCE;

    static {
        BUILDER.push("General Settings");
        WEBHOOK_URL = BUILDER.comment("The full URL of the webhook endpoint to notify when player events occur.")
                .define("webhookUrl", "http://localhost:3000/api/minecraft/sync-balance");
        API_KEY = BUILDER.comment("API key to be sent in the 'x-api-key' header for authorization.")
                .define("apiKey", "");

        BANK_SYNC_URL = BUILDER.comment("The full URL of the separate endpoint to notify with bank balance and user details when bank deposits/withdrawals occur.")
                .define("bankSyncUrl", "http://localhost:3000/api/minecraft/bank-sync");

        PLAYER_API_URL = BUILDER.comment("The full URL of the player API endpoint to retrieve player roles.")
                .define("playerApiUrl", "http://localhost:3000/api/minecraft/player");

        TAX_COLLECTOR_FORCE_ACCEPTANCE = BUILDER.comment("Whether newly created Tax Collectors will default to Force Acceptance being enabled.")
                .define("taxCollectorForceAcceptance", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
