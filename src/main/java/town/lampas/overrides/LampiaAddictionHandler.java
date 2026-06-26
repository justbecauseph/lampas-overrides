package town.lampas.overrides;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Drives the Lampia cheese addiction. Once per second per online player it checks how long it's
 * been since their last bite; if they've gone past their (addiction- and rat-adjusted) grace
 * period, it applies/refreshes {@link LampiaWithdrawalEffect}. Prolonged abstinence slowly tapers
 * the addiction back down. Mirrors the throttled {@link CheesyContactHandler} pattern.
 */
public class LampiaAddictionHandler {

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return; // throttle to ~1x/sec

        int level = LampiaAddiction.getLevel(player);
        if (level <= 0) {
            if (player.hasEffect(ModEffects.LAMPIA_WITHDRAWAL)) {
                player.removeEffect(ModEffects.LAMPIA_WITHDRAWAL);
            }
            return;
        }

        long now = player.level().getGameTime();
        long ticksSince = now - LampiaAddiction.getLastEaten(player);
        boolean rat = PlayerActivityListener.isRat(player.getUUID());

        // Grace period shrinks the deeper the addiction; rats get only a fraction of it.
        int base = ModConfig.LAMPIA_BASE_GRACE_TICKS.get();
        int step = ModConfig.LAMPIA_GRACE_STEP_TICKS.get();
        int floor = ModConfig.LAMPIA_GRACE_FLOOR_TICKS.get();
        long grace = Math.max(floor, (long) base - (long) step * level);
        if (rat) {
            grace = (long) (grace * ModConfig.LAMPIA_RAT_GRACE_MULTIPLIER.get());
        }

        if (ticksSince <= grace) return; // still satisfied — no withdrawal

        // In withdrawal: severity scales with how deep the addiction is and how overdue the fix is.
        long overdue = ticksSince - grace;
        int amplifier = (int) Math.min(level / 4 + overdue / Math.max(1L, grace), 9);
        if (rat) {
            amplifier += ModConfig.LAMPIA_RAT_WITHDRAWAL_BONUS.get();
        }
        amplifier = Math.min(amplifier, 9);

        // Keep the sickness topped up, but only resync to the client when it's actually needed
        // (amplifier changed, or the effect is missing / about to lapse) — re-applying every pass
        // would send a needless mob-effect packet each second. onConsume clears it outright on eat.
        MobEffectInstance current = player.getEffect(ModEffects.LAMPIA_WITHDRAWAL);
        if (current == null || current.getAmplifier() != amplifier || current.getDuration() < 25) {
            player.addEffect(new MobEffectInstance(ModEffects.LAMPIA_WITHDRAWAL, 100, amplifier, false, true, true));
        }

        // Suffering through abstinence slowly weans the addiction down.
        int taper = ModConfig.LAMPIA_TAPER_TICKS.get();
        if (now - LampiaAddiction.getLastDecay(player) >= taper) {
            LampiaAddiction.setLevel(player, level - 1);
            LampiaAddiction.setLastDecay(player, now);
        }
    }
}
