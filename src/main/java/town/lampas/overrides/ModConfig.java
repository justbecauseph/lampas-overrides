package town.lampas.overrides;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ModConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> WEBHOOK_URL;
    public static final ModConfigSpec.ConfigValue<String> API_KEY;

    static {
        BUILDER.push("General Settings");
        WEBHOOK_URL = BUILDER.comment("The full URL of the webhook endpoint to notify when player events occur.")
                .define("webhookUrl", "http://localhost:3000/api/minecraft/sync-balance");
        API_KEY = BUILDER.comment("API key to be sent in the 'x-api-key' header for authorization.")
                .define("apiKey", "");
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
