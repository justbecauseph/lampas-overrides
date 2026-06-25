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

    @Override
    public boolean applyEffectTick(LivingEntity livingEntity, int amplifier) {
        // Withdrawal damage scales with how deep the addiction is (the amplifier). Leave a one-heart
        // buffer so it never kills outright.
        if (livingEntity.getHealth() > 2.0F) {
            livingEntity.hurt(livingEntity.damageSources().magic(), 1.0F + (float) amplifier);
        }
        // Cravings burn energy: the shakes make you hungry on top of the damage.
        if (livingEntity instanceof Player player) {
            player.causeFoodExhaustion(0.025F * (float) (amplifier + 1));
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // Fire faster the worse the withdrawal: amp0 ~ every 2s, amp1 ~ 1s, amp2 ~ 0.5s, ...
        int interval = 40 >> amplifier;
        return interval > 0 ? duration % interval == 0 : true;
    }
}
