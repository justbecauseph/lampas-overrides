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
    public static final ModConfigSpec.ConfigValue<String> PRONOUNDB_API_URL;

    public static final ModConfigSpec.BooleanValue SHOW_SOCIAL_ICONS;
    public static final ModConfigSpec.BooleanValue SHOW_PRONOUNS;

    public static final ModConfigSpec.BooleanValue PLAGUE_MODE;

    public static final ModConfigSpec.IntValue LAMPIA_BASE_GRACE_TICKS;
    public static final ModConfigSpec.IntValue LAMPIA_GRACE_STEP_TICKS;
    public static final ModConfigSpec.IntValue LAMPIA_GRACE_FLOOR_TICKS;
    public static final ModConfigSpec.IntValue LAMPIA_ADDICTION_MAX;
    public static final ModConfigSpec.IntValue LAMPIA_TAPER_TICKS;
    public static final ModConfigSpec.DoubleValue LAMPIA_RAT_GRACE_MULTIPLIER;
    public static final ModConfigSpec.IntValue LAMPIA_RAT_WITHDRAWAL_BONUS;

    public static final ModConfigSpec.ConfigValue<Boolean> TAX_COLLECTOR_FORCE_ACCEPTANCE;
    public static final ModConfigSpec.BooleanValue TAX_COLLECTOR_MINIMUM_TAX;

    public static final ModConfigSpec.EnumValue<ClickMode> LADDER_MODE;
    public static final ModConfigSpec.IntValue LADDER_PICK_UP_LIMIT;
    public static final ModConfigSpec.IntValue LADDER_STEP_UP_LIMIT;
    public static final ModConfigSpec.BooleanValue LADDER_ALLOW_LIVING_ENTITIES;
    public static final ModConfigSpec.BooleanValue LADDER_ALLOW_PLAYERS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> LADDER_EXCLUDED_LIVING_ENTITIES;
    public static final ModConfigSpec.BooleanValue LADDER_RIDE_EXTENSION;
    public static final ModConfigSpec.BooleanValue LADDER_ALLOW_INTERACTIONS;

    public static final ModConfigSpec.BooleanValue BLOCK_AUDIT_ENABLED;
    public static final ModConfigSpec.IntValue BLOCK_AUDIT_FLUSH_INTERVAL_MS;
    public static final ModConfigSpec.IntValue BLOCK_AUDIT_BATCH_SIZE;
    public static final ModConfigSpec.IntValue BLOCK_AUDIT_MAX_QUEUE;
    public static final ModConfigSpec.IntValue BLOCK_AUDIT_RETENTION_DAYS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> BLOCK_AUDIT_IGNORED_BLOCKS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> BLOCK_AUDIT_WATCHED_ITEMS;

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

        PRONOUNDB_API_URL = BUILDER.comment("The PronounDB v2 lookup endpoint used to fetch players' pronouns by Minecraft UUID. Public endpoint (no API key needed).")
                .define("pronounDbApiUrl", "https://pronoundb.org/api/v2/lookup");

        SHOW_PRONOUNS = BUILDER.comment("Whether to prepend each player's PronounDB pronouns (e.g. [he/him]) to their display name in chat, the tab list, nametags and Jade. Server-side; applies to everyone, no client mod needed.")
                .define("showPronouns", true);

        PLAGUE_MODE = BUILDER.comment("If true, Cheesy/Lactose Intolerant are replaced with Plague/Infected. Toggle in-game with /plaguemode to migrate already-applied effects on online players live; editing this value directly only affects newly-applied effects.")
                .define("plagueMode", false);

        TAX_COLLECTOR_FORCE_ACCEPTANCE = BUILDER.comment("Whether newly created Tax Collectors will default to Force Acceptance being enabled.")
                .define("taxCollectorForceAcceptance", true);
        TAX_COLLECTOR_MINIMUM_TAX = BUILDER.comment("If true, any taxed trade collects at least the smallest coin (1 Aur) instead of rounding down to nothing. Prevents low-value trades (e.g. a 5% tax on a 5 Aur trade) from being taxed zero.")
                .define("taxCollectorMinimumTax", true);
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

        BUILDER.push("Block Audit Settings");
        BLOCK_AUDIT_ENABLED = BUILDER.comment("Whether to record block placements/destructions by players to a local SQLite audit log (world/lampas_audit.db).")
                .define("blockAuditEnabled", true);
        BLOCK_AUDIT_FLUSH_INTERVAL_MS = BUILDER.comment("How often (in milliseconds) the background thread flushes buffered block events to disk.")
                .defineInRange("blockAuditFlushIntervalMs", 2000, 250, 60000);
        BLOCK_AUDIT_BATCH_SIZE = BUILDER.comment("Maximum number of block events written per flush transaction.")
                .defineInRange("blockAuditBatchSize", 1000, 1, 100000);
        BLOCK_AUDIT_MAX_QUEUE = BUILDER.comment("Maximum number of pending block events held in memory before new events are dropped (backpressure safety valve to protect the server thread under extreme load).")
                .defineInRange("blockAuditMaxQueue", 200000, 1000, 5000000);
        BLOCK_AUDIT_RETENTION_DAYS = BUILDER.comment("Delete audit rows older than this many days on server start. 0 keeps everything forever.")
                .defineInRange("blockAuditRetentionDays", 0, 0, 3650);
        BLOCK_AUDIT_IGNORED_BLOCKS = BUILDER.comment("Block IDs to skip auditing entirely (e.g. high-churn blocks). Example: [\"minecraft:tnt\", \"minecraft:fire\"]")
                .defineList("blockAuditIgnoredBlocks", () -> java.util.List.of(), object -> object instanceof String);
        BLOCK_AUDIT_WATCHED_ITEMS = BUILDER.comment("Item IDs that should never legitimately be in a survival player's inventory. When a non-creative player is found holding one, an 'obtain' audit entry + console warning is recorded (catches creative-menu/give/pickup/dupe). Empty list disables the scan.")
                .defineList("blockAuditWatchedItems", () -> java.util.List.of("minecraft:bedrock"), object -> object instanceof String);
        BUILDER.pop();

        BUILDER.push("Lampia Cheese Settings");
        LAMPIA_BASE_GRACE_TICKS = BUILDER.comment("Base grace period (in ticks, 20 = 1s) after eating Lampia cheese before withdrawal can set in, at addiction level 0. Default 6000 = 5 minutes.")
                .defineInRange("lampiaBaseGraceTicks", 6000, 20, 1728000);
        LAMPIA_GRACE_STEP_TICKS = BUILDER.comment("How many ticks the grace period shrinks per addiction level (progressive tolerance). Default 240 = 12s shorter per level.")
                .defineInRange("lampiaGraceStepTicks", 240, 0, 72000);
        LAMPIA_GRACE_FLOOR_TICKS = BUILDER.comment("Minimum grace period (in ticks) no matter how deep the addiction. Default 600 = 30 seconds.")
                .defineInRange("lampiaGraceFloorTicks", 600, 20, 1728000);
        LAMPIA_ADDICTION_MAX = BUILDER.comment("Maximum addiction level a player can reach. Higher = shorter grace and harsher withdrawal cap. Default 20.")
                .defineInRange("lampiaAddictionMax", 20, 1, 1000);
        LAMPIA_TAPER_TICKS = BUILDER.comment("While abstaining (in withdrawal), addiction level drops by 1 every this many ticks. Default 12000 = 10 minutes.")
                .defineInRange("lampiaTaperTicks", 12000, 20, 1728000);
        LAMPIA_RAT_GRACE_MULTIPLIER = BUILDER.comment("Grace-period multiplier for players flagged as rats (Lampia is especially addictive to them). 0.5 = rats get half the grace before withdrawal.")
                .defineInRange("lampiaRatGraceMultiplier", 0.5D, 0.01D, 1.0D);
        LAMPIA_RAT_WITHDRAWAL_BONUS = BUILDER.comment("Extra withdrawal amplifier (severity) added for players flagged as rats. Default 1.")
                .defineInRange("lampiaRatWithdrawalBonus", 1, 0, 9);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public enum ClickMode {
        RIDE,
        PICK_UP,
        DO_NOTHING
    }
}

