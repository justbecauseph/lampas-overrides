package town.lampas.overrides;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The "Lampia Withdrawal" sickness. Applied (and continually refreshed) by
 * {@link LampiaAddictionHandler} once a player who has been eating Lampia cheese goes too long
 * without another bite. Unlike the inert effect stubs ({@link CheesyEffect} etc.), this one is
 * active: it chips away at the player's health and makes them hungry the longer they abstain.
 *
 * <p>Damage is deliberately non-lethal (it stops once the player is near death) so a deeply
 * addicted player can't be locked in a death/respawn loop — the addiction state survives death,
 * so a lethal withdrawal would keep killing them on every respawn.
 */
public class LampiaWithdrawalEffect extends MobEffect {
    public LampiaWithdrawalEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    /** Withdrawal can hurt you, but never kill you: always leave at least this much health (two hearts). */
    private static final float HEALTH_FLOOR = 4.0F;

    @Override
    public boolean applyEffectTick(LivingEntity livingEntity, int amplifier) {
        // Withdrawal damage scales with how deep the addiction is (the amplifier), but it must never be
        // lethal. The old guard only refused to *start* a hit near death — a high amplifier (up to 10
        // damage) could still overshoot the buffer and kill in one tick. Instead, cap the damage to the
        // health above the floor so the player is always left with at least half a heart. (magic damage
        // can only be reduced further by resistance/protection/absorption, so the clamp is a safe upper
        // bound on health actually lost.)
        float survivable = livingEntity.getHealth() - HEALTH_FLOOR;
        if (survivable > 0.0F) {
            livingEntity.hurt(livingEntity.damageSources().magic(), Math.min(0.5F + 0.5F * (float) amplifier, survivable));
        }
        // Cravings burn energy: the shakes make you hungry on top of the damage.
        if (livingEntity instanceof Player player) {
            player.causeFoodExhaustion(0.0125F * (float) (amplifier + 1));
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // Fire faster the worse the withdrawal: amp0 ~ every 4s, amp1 ~ 2s, amp2 ~ 1s, ...
        int interval = 80 >> amplifier;
        return interval > 0 ? duration % interval == 0 : true;
    }
}
