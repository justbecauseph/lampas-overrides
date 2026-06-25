package town.lampas.overrides;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(LampasOverridesMod.MODID)
public class LampasOverridesMod {
    public static final String MODID = "lampas_overrides";
    private static final Logger LOGGER = LogUtils.getLogger();

    public LampasOverridesMod(ModContainer modContainer, IEventBus modEventBus) {
        LOGGER.info("Lampas Overrides (Mixin Edition) initialized! Trigger build.");
        
        // Register the common configuration (saved in config/lampas_overrides-common.toml)
        modContainer.registerConfig(ModConfig.Type.COMMON, town.lampas.overrides.ModConfig.SPEC);
        
        // Register custom blocks, items and effects (blocks first so block items can reference them)
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEffects.MOB_EFFECTS.register(modEventBus);

        // Add creative tab listener
        modEventBus.addListener(this::addCreativeContents);
        
        // Register the player activity listener to the global game event bus
        NeoForge.EVENT_BUS.register(new PlayerActivityListener());

        // Register the block placement/destruction audit listener (local SQLite log)
        NeoForge.EVENT_BUS.register(new town.lampas.overrides.audit.BlockAuditHandler());
        // Refresh the audit handler's cached lookup sets on config (re)load
        modEventBus.addListener(town.lampas.overrides.audit.BlockAuditHandler::onConfigEvent);
        
        // Register PlayerLadderHandler config listener on mod event bus
        modEventBus.addListener(PlayerLadderHandler::onConfigEvent);
        
        // Register PlayerLadderHandler to the global game event bus
        NeoForge.EVENT_BUS.register(new PlayerLadderHandler());

        // Prepend PronounDB pronouns ([he/him]) to player names. Registered before LiveStatusHandler
        // so the [LIVE] tag ends up outermost: [LIVE] [he/him] Name.
        NeoForge.EVENT_BUS.register(new PronounStatusHandler());

        // Register the /live toggle handler ([LIVE] chat/tab prefix, persisted in player NBT)
        NeoForge.EVENT_BUS.register(new LiveStatusHandler());

        // Register the Cheesy/Plague contact-detection handler to the global game event bus
        NeoForge.EVENT_BUS.register(new CheesyContactHandler());

        // Register the /plaguemode admin toggle command
        NeoForge.EVENT_BUS.register(new PlagueModeCommand());

        // Register the Lampia cheese addiction/withdrawal handler
        NeoForge.EVENT_BUS.register(new LampiaAddictionHandler());

        // Echo global chat (player chat + /say, /me, /tellraw, joins/leaves/deaths/advancements) to a Discord webhook
        NeoForge.EVENT_BUS.register(new town.lampas.overrides.chat.ChatRelayHandler());


        // Client-only: prepend YouTube/Twitch chat icons. Gated on the client dist so the
        // client-only ChatSocialDecorator class never loads on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.register(new ChatSocialDecorator());
        }
    }

    private void addCreativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.BOOK_OF_ELDRITCH.get());
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.ELDRITCH_STONE.get());
        }
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(ModItems.LAMPIA_CHEESE_SLICE.get());
            event.accept(ModItems.LAMPIA_CHEESE_WHEEL.get());
        }
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModItems.LAMPIA_CHEESE_BLOCK.get());
        }
    }
}
