package town.lampas.overrides;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player Lampia cheese addiction bookkeeping. Conrad &amp; Crowley's "cheese" is engineered to
 * hook you: every bite deepens your addiction (a hidden level), and once you stop eating the
 * {@link LampiaWithdrawalEffect} sets in. The longer and deeper the addiction, the shorter the
 * grace period before withdrawal and the harsher it bites — see {@link LampiaAddictionHandler}.
 *
 * <p>State lives in the player's persisted NBT (the {@code PlayerPersisted} subtag, mirroring
 * {@link LiveStatusHandler}), so an addiction survives both reconnects and death. This is a static
 * helper in the style of {@link CarrierStatus}.
 */
public final class LampiaAddiction {
    private LampiaAddiction() {}

    private static final String LEVEL_KEY = "lampia_level";
    private static final String LAST_EATEN_KEY = "lampia_last_eaten";
    private static final String LAST_DECAY_KEY = "lampia_last_decay";

    /**
     * Read-only view of the persisted subtag, or {@code null} if the player has none yet. Unlike
     * {@link CompoundTag#getCompound} this never allocates a throwaway empty tag, so it's cheap to
     * call on the per-tick hot path for players who've never touched Lampia cheese.
     */
    private static CompoundTag readOnly(Player player) {
        CompoundTag root = player.getPersistentData();
        return root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)
                ? root.getCompound(Player.PERSISTED_NBT_TAG)
                : null;
    }

    /** Mutable persisted subtag (allocates if absent); use only on the write paths. */
    private static CompoundTag mutable(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void store(Player player, CompoundTag persisted) {
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static int getLevel(Player player) {
        CompoundTag tag = readOnly(player);
        return tag == null ? 0 : tag.getInt(LEVEL_KEY);
    }

    public static long getLastEaten(Player player) {
        CompoundTag tag = readOnly(player);
        return tag == null ? 0L : tag.getLong(LAST_EATEN_KEY);
    }

    public static long getLastDecay(Player player) {
        CompoundTag tag = readOnly(player);
        return tag == null ? 0L : tag.getLong(LAST_DECAY_KEY);
    }

    /** Sets the addiction level (clamped to [0, configured max]) and persists it. */
    public static void setLevel(Player player, int level) {
        int clamped = Math.max(0, Math.min(level, ModConfig.LAMPIA_ADDICTION_MAX.get()));
        CompoundTag tag = mutable(player);
        tag.putInt(LEVEL_KEY, clamped);
        store(player, tag);
    }

    /** Records the moment the addiction level last ticked down (used to pace the taper). */
    public static void setLastDecay(Player player, long gameTime) {
        CompoundTag tag = mutable(player);
        tag.putLong(LAST_DECAY_KEY, gameTime);
        store(player, tag);
    }

    /**
     * Called when a player finishes eating a piece of Lampia cheese. Deepens the addiction by
     * {@code potency}, refreshes the "last eaten" timestamp (resetting the withdrawal clock) and
     * clears any active withdrawal — the fix is in, the shakes stop.
     */
    public static void onConsume(ServerPlayer player, int potency) {
        int before = getLevel(player);
        long now = player.level().getGameTime();

        CompoundTag tag = mutable(player);
        tag.putInt(LEVEL_KEY, Math.min(before + potency, ModConfig.LAMPIA_ADDICTION_MAX.get()));
        tag.putLong(LAST_EATEN_KEY, now);
        tag.putLong(LAST_DECAY_KEY, now);
        store(player, tag);

        player.removeEffect(ModEffects.LAMPIA_WITHDRAWAL);

        // The reward: every bite delivers a euphoric "rush". This is what makes the cheese enticing
        // (and the addiction worth feeding) rather than just a delayed punishment.
        applyRush(player, potency, getLevel(player));

        // First taste hooks them — a subtle nudge so the mechanic is discoverable.
        if (before == 0) {
            player.sendSystemMessage(Component.literal("The Lampia cheese is strangely moreish... you already want more.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * The Lampia "rush" — the euphoric high from a bite of cheese. A bigger serving ({@code potency})
     * lasts longer, and the rush <em>intensifies</em> the deeper the addiction ({@code level}): a
     * hooked player chases an ever-stronger high, which is exactly what keeps them eating. All effects
     * are beneficial and re-applying simply refreshes them, so back-to-back bites stack their duration
     * rather than fighting each other.
     */
    private static void applyRush(ServerPlayer player, int potency, int level) {
        if (!ModConfig.LAMPIA_RUSH_ENABLED.get()) return;

        int duration = ModConfig.LAMPIA_RUSH_BASE_TICKS.get() * potency;
        int intensify = level / 5; // every 5 levels of addiction adds a tier to the euphoria

        // Sustained euphoria: you feel light, fast and sharp for the length of the high.
        addBuff(player, MobEffects.MOVEMENT_SPEED, duration, Math.min(potency - 1 + intensify, 3));
        addBuff(player, MobEffects.DIG_SPEED, duration, Math.min(potency - 1 + intensify, 3));
        addBuff(player, MobEffects.JUMP, duration, Math.min(potency - 1, 2));

        // Invincible swagger: the high makes you feel untouchable and strong.
        addBuff(player, MobEffects.FIRE_RESISTANCE, duration, 0);
        addBuff(player, MobEffects.DAMAGE_BOOST, duration, Math.min(potency - 1 + intensify, 3));

        // Short, intense bursts: the warm glow of the hit and the instant "full and content" feeling.
        addBuff(player, MobEffects.REGENERATION, 20 * (potency + 1), Math.min(intensify, 2));
        addBuff(player, MobEffects.SATURATION, Math.max(1, potency), 0);

        // A whole wheel is a serious dose — the world briefly lights up.
        if (potency >= 3) {
            addBuff(player, MobEffects.NIGHT_VISION, duration + 200, 0); // +10s so it fades without flicker
        }
    }

    /** Applies a non-ambient, visible beneficial effect (shows the HUD icon and particles). */
    private static void addBuff(ServerPlayer player, Holder<MobEffect> effect, int duration, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true, true));
    }

    /**
     * Wipes the addiction clean — resets the hidden level to zero, clears the withdrawal clock and
     * removes any active {@link LampiaWithdrawalEffect}. Used by the antidote
     * ({@link LampiaCureItem}). Returns {@code true} if the player actually had an addiction (or was
     * mid-withdrawal) to cure, so callers can tailor their feedback.
     */
    public static boolean cure(ServerPlayer player) {
        boolean wasAddicted = getLevel(player) > 0 || player.hasEffect(ModEffects.LAMPIA_WITHDRAWAL);

        CompoundTag tag = mutable(player);
        tag.putInt(LEVEL_KEY, 0);
        tag.putLong(LAST_EATEN_KEY, 0L);
        tag.putLong(LAST_DECAY_KEY, 0L);
        store(player, tag);

        player.removeEffect(ModEffects.LAMPIA_WITHDRAWAL);

        if (wasAddicted) {
            player.sendSystemMessage(Component.literal("The craving fades. Your head clears — you're free of the Lampia cheese.")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
        } else {
            player.sendSystemMessage(Component.literal("The tonic is bitter, but you weren't craving anything to begin with.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        return wasAddicted;
    }
}
