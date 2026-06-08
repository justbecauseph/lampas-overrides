package town.lampas.overrides;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
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
        
        // Register custom items and effects
        ModItems.ITEMS.register(modEventBus);
        ModEffects.MOB_EFFECTS.register(modEventBus);

        // Add creative tab listener
        modEventBus.addListener(this::addCreativeContents);
        
        // Register the player activity listener to the global game event bus
        NeoForge.EVENT_BUS.register(new PlayerActivityListener());
        
        // Register PlayerLadderHandler config listener on mod event bus
        modEventBus.addListener(PlayerLadderHandler::onConfigEvent);
        
        // Register PlayerLadderHandler to the global game event bus
        NeoForge.EVENT_BUS.register(new PlayerLadderHandler());
    }

    private void addCreativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.BOOK_OF_ELDRITCH.get());
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.ELDRITCH_STONE.get());
        }
    }
}
