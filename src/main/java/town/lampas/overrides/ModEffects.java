package town.lampas.overrides;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, LampasOverridesMod.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> TOTEM_EFFECT = MOB_EFFECTS.register("totem_of_undying",
            () -> new TotemOfUndyingEffect(MobEffectCategory.BENEFICIAL, 0xFDC52F));

    public static final DeferredHolder<MobEffect, MobEffect> FISHERIES_BOON = MOB_EFFECTS.register("fisheries_boon",
            () -> new FisheriesBoonEffect(MobEffectCategory.BENEFICIAL, 0x3CA4C7));
}
