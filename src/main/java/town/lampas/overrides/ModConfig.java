package town.lampas.overrides;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ModConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> WEBHOOK_URL;
    public static final ModConfigSpec.ConfigValue<String> API_KEY;

    public static final ModConfigSpec.ConfigValue<String> BANK_SYNC_URL;
    public static final ModConfigSpec.ConfigValue<String> PLAYER_API_URL;
    public static final ModConfigSpec.ConfigValue<String> BOUNTIES_API_URL;
    public static final ModConfigSpec.ConfigValue<String> PLAYER_DEATH_API_URL;
    public static final ModConfigSpec.ConfigValue<String> SOCIALS_API_URL;

    public static final ModConfigSpec.BooleanValue SHOW_SOCIAL_ICONS;

    public static final ModConfigSpec.ConfigValue<Boolean> TAX_COLLECTOR_FORCE_ACCEPTANCE;

    public static final ModConfigSpec.EnumValue<ClickMode> LADDER_MODE;
    public static final ModConfigSpec.IntValue LADDER_PICK_UP_LIMIT;
    public static final ModConfigSpec.IntValue LADDER_STEP_UP_LIMIT;
    public static final ModConfigSpec.BooleanValue LADDER_ALLOW_LIVING_ENTITIES;
    public static final ModConfigSpec.BooleanValue LADDER_ALLOW_PLAYERS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> LADDER_EXCLUDED_LIVING_ENTITIES;
    public static final ModConfigSpec.BooleanValue LADDER_RIDE_EXTENSION;
    public static final ModConfigSpec.BooleanValue LADDER_ALLOW_INTERACTIONS;

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

        BOUNTIES_API_URL = BUILDER.comment("The full URL of the bounties API endpoint to retrieve and claim bounties.")
                .define("bountiesApiUrl", "http://localhost:3000/api/minecraft/bounties");

        PLAYER_DEATH_API_URL = BUILDER.comment("The full URL of the player death API endpoint to notify when a player dies.")
                .define("playerDeathApiUrl", "http://localhost:3000/api/minecraft/player-death");

        SOCIALS_API_URL = BUILDER.comment("The full URL of the socials API endpoint used to fetch players' linked YouTube/Twitch channels for chat icons. This is a public endpoint (no API key needed), so the default points at the live web app for client modpacks.")
                .define("socialsApiUrl", "https://portal.lampas.town/api/minecraft/socials");

        SHOW_SOCIAL_ICONS = BUILDER.comment("Whether to prepend YouTube/Twitch icons to chat messages from players who have linked a channel. Client-side only.")
                .define("showSocialIcons", true);

        TAX_COLLECTOR_FORCE_ACCEPTANCE = BUILDER.comment("Whether newly created Tax Collectors will default to Force Acceptance being enabled.")
                .define("taxCollectorForceAcceptance", true);
        BUILDER.pop();

        BUILDER.push("Player Ladder Settings");
        LADDER_MODE = BUILDER.comment("Action that occurs when you click on a player/entity.")
                .defineEnum("rightClickMode", ClickMode.RIDE);
        LADDER_PICK_UP_LIMIT = BUILDER.comment("Limits how many entities a player can pick up.")
                .defineInRange("pickUpLimit", 16, 1, Integer.MAX_VALUE);
        LADDER_STEP_UP_LIMIT = BUILDER.comment("Limits how many entities up a player can go.")
                .defineInRange("stepUpLimit", 16, 1, Integer.MAX_VALUE);
        LADDER_ALLOW_LIVING_ENTITIES = BUILDER.comment("Allows riding or picking up any living entity.")
                .define("allowLivingEntities", false);
        LADDER_ALLOW_PLAYERS = BUILDER.comment("Allows riding or picking up any player.")
                .define("allowPlayers", true);
        LADDER_EXCLUDED_LIVING_ENTITIES = BUILDER.comment("The list of living entities that can't be ridden/picked up. Supports entity tags e.g: #minecraft:dismounts_underwater")
                .defineList("excludedLivingEntities",
                        () -> java.util.List.of("minecraft:wither", "minecraft:ender_dragon", "minecraft:minecart", "#minecraft:boat", "#minecraft:dismounts_underwater"), object -> true);
        LADDER_RIDE_EXTENSION = BUILDER.comment("Allows the /ride command to mount entities on top of players.")
                .define("rideCommandExtension", true);
        LADDER_ALLOW_INTERACTIONS = BUILDER.comment("Allows interacting with the world while there's an entity on top of you.")
                .define("allowInteractions", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public enum ClickMode {
        RIDE,
        PICK_UP,
        DO_NOTHING
    }
}

