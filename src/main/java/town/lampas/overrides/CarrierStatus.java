package town.lampas.overrides;

import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public final class CarrierStatus {
    private CarrierStatus() {}

    public static Holder<MobEffect> getCarrierEffect() {
        return ModConfig.PLAGUE_MODE.get() ? ModEffects.PLAGUE : ModEffects.CHEESY;
    }

    public static Holder<MobEffect> getContactEffect() {
        return ModConfig.PLAGUE_MODE.get() ? ModEffects.INFECTED : ModEffects.LACTOSE_INTOLERANT;
    }

    /**
     * Swaps already-applied carrier/contact effects on all online players to match the current
     * {@link ModConfig#PLAGUE_MODE} value, preserving remaining duration. Call right after the
     * config flag changes so a /plaguemode toggle takes effect immediately without a reconnect.
     */
    public static void migrateAppliedEffects(MinecraftServer server) {
        boolean plague = ModConfig.PLAGUE_MODE.get();
        Holder<MobEffect> fromCarrier = plague ? ModEffects.CHEESY : ModEffects.PLAGUE;
        Holder<MobEffect> fromContact = plague ? ModEffects.LACTOSE_INTOLERANT : ModEffects.INFECTED;
        Holder<MobEffect> toCarrier = getCarrierEffect();
        Holder<MobEffect> toContact = getContactEffect();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            swapEffect(player, fromCarrier, toCarrier);
            swapEffect(player, fromContact, toContact);
        }
    }

    private static void swapEffect(ServerPlayer player, Holder<MobEffect> from, Holder<MobEffect> to) {
        MobEffectInstance existing = player.getEffect(from);
        if (existing == null) {
            return;
        }
        MobEffectInstance replacement = new MobEffectInstance(to, existing.getDuration(), existing.getAmplifier(),
                existing.isAmbient(), existing.isVisible(), existing.showIcon());
        player.removeEffect(from);
        player.addEffect(replacement);
    }
}
