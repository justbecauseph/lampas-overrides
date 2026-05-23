package town.lampas.overrides;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(LampasOverridesMod.MODID)
public class LampasOverridesMod {
    public static final String MODID = "lampas_overrides";
    private static final Logger LOGGER = LogUtils.getLogger();

    public LampasOverridesMod() {
        LOGGER.info("Lampas Trade Overrides (Mixin Edition) initialized!");
    }
}
