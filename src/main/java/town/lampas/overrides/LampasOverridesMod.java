package town.lampas.overrides;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(LampasOverridesMod.MODID)
public class LampasOverridesMod {
    public static final String MODID = "lampas_overrides";
    private static final Logger LOGGER = LogUtils.getLogger();

    public LampasOverridesMod(ModContainer modContainer) {
        LOGGER.info("Lampas Trade Overrides (Mixin Edition) initialized!");
        
        // Register the common configuration (saved in config/lampas_overrides-common.toml)
        modContainer.registerConfig(ModConfig.Type.COMMON, town.lampas.overrides.ModConfig.SPEC);
        
        // Register the player activity listener to the global game event bus
        NeoForge.EVENT_BUS.register(new PlayerActivityListener());
    }
}
