package town.lampas.overrides;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * "Heal Touch" player buff. Any damage dealt <em>by</em> a player carrying the configured
 * scoreboard tag (default {@code heal_touch}, granted with {@code /tag <player> add heal_touch})
 * is cancelled outright, and the target is healed instead. The damage the player would have dealt
 * becomes healing for whatever they hit.
 *
 * <p>Implemented on {@link LivingIncomingDamageEvent}, the earliest cancellable damage hook, so the
 * hit is negated cleanly before armour/effect mitigation — no one-tick flash, no chance of a big
 * hit slipping through and killing the target first (the datapack version could not guarantee
 * that). Works for melee and ranged alike: {@link DamageSource#getEntity()} resolves to the player
 * who caused the damage, i.e. the shooter for arrows/tridents, not the projectile.
 *
 * <p>{@code healTouchHealMultiplier} scales how much the target heals relative to the cancelled
 * damage: {@code 1.0} (default) turns the damage exactly into healing, {@code 0.0} makes the player
 * merely unable to hurt the target (immune, no heal), and {@code 2.0} over-heals.
 *
 * <p>Hot path: this fires for every damage event server-wide, so all config values are cached in
 * volatile fields on config (re)load rather than read from the config spec per event.
 */
public class HealTouchHandler {

    /** Config values cached on (re)load so the damage hot path never touches the config spec. */
    private static volatile boolean enabled = false;
    private static volatile String tag = "heal_touch";
    private static volatile double healMult = 1.0;
    private static volatile boolean affectPlayers = true;

    /** Refreshes the cached config values whenever this mod's config is loaded or reloaded. */
    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == ModConfig.SPEC) {
            enabled = ModConfig.HEAL_TOUCH_ENABLED.get();
            tag = ModConfig.HEAL_TOUCH_TAG.get();
            healMult = ModConfig.HEAL_TOUCH_HEAL_MULT.get();
            affectPlayers = ModConfig.HEAL_TOUCH_AFFECT_PLAYERS.get();
        }
    }

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!enabled) return;

        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();          // causing entity (shooter for projectiles)
        if (!(attacker instanceof Player player)) return;
        if (!player.getTags().contains(tag)) return;

        // Optionally leave PvP damage alone so a medic can't accidentally make players unkillable.
        if (target instanceof Player && !affectPlayers) return;

        event.setCanceled(true);                       // no damage is taken

        if (healMult > 0.0) {
            target.heal((float) (event.getAmount() * healMult));
        }
    }
}
