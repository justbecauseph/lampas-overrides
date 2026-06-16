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

    public static final DeferredHolder<MobEffect, MobEffect> CHEESY = MOB_EFFECTS.register("cheesy",
            () -> new CheesyEffect(MobEffectCategory.NEUTRAL, 0xF6C90E));

    public static final DeferredHolder<MobEffect, MobEffect> PLAGUE = MOB_EFFECTS.register("plague",
            () -> new PlagueEffect(MobEffectCategory.HARMFUL, 0x6B8E23));

    public static final DeferredHolder<MobEffect, MobEffect> LACTOSE_INTOLERANT = MOB_EFFECTS.register("lactose_intolerant",
            () -> new LactoseIntolerantEffect(MobEffectCategory.HARMFUL, 0xE0D8C3));

    public static final DeferredHolder<MobEffect, MobEffect> INFECTED = MOB_EFFECTS.register("infected",
            () -> new InfectedEffect(MobEffectCategory.HARMFUL, 0x4F6F2E));
}
