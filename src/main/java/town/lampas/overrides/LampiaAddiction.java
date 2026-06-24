package town.lampas.overrides;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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

        // First taste hooks them — a subtle nudge so the mechanic is discoverable.
        if (before == 0) {
            player.sendSystemMessage(Component.literal("The Lampia cheese is strangely moreish... you already want more.")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
