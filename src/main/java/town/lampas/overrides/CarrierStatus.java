package town.lampas.overrides;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

public final class CarrierStatus {
    private CarrierStatus() {}

    public static Holder<MobEffect> getCarrierEffect() {
        return ModConfig.PLAGUE_MODE.get() ? ModEffects.PLAGUE : ModEffects.CHEESY;
    }

    public static Holder<MobEffect> getContactEffect() {
        return ModConfig.PLAGUE_MODE.get() ? ModEffects.INFECTED : ModEffects.LACTOSE_INTOLERANT;
    }
}
