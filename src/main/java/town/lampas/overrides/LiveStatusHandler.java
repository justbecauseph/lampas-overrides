package town.lampas.overrides;

import com.mojang.brigadier.Command;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import town.lampas.overrides.mixin.PlayerDisplayNameAccessor;

/**
 * Server-side "live" toggle. Players run {@code /live toggle} (or {@code /live on|off}) to mark
 * themselves as streaming, which prepends a red {@code [LIVE]} tag to their name in chat and the
 * tab list for everyone. The flag is stored in the player's persisted NBT (the "PlayerPersisted"
 * subtag), so it survives both death and reconnect.
 *
 * <p>Chat picks up the prefix because the chat sender name is derived from
 * {@link Player#getDisplayName()} (via {@link PlayerEvent.NameFormat}); the tab list uses
 * {@link ServerPlayer#getTabListDisplayName()} (via {@link PlayerEvent.TabListNameFormat}).
 */
public class LiveStatusHandler {
    private static final String LIVE_KEY = "lampas_live";

    private static final Component LIVE_PREFIX =
            Component.literal("[LIVE] ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

    public static boolean isLive(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return persisted.getBoolean(LIVE_KEY);
    }

    private static void setLive(Player player, boolean live) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putBoolean(LIVE_KEY, live);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** Re-broadcasts the player's name to clients so the [LIVE] prefix appears/disappears immediately. */
    private static void refreshNames(ServerPlayer player) {
        // Invalidate NeoForge's cached display name instead of calling refreshDisplayName():
        // refreshDisplayName() re-fires NameFormat with the raw getName(), which bypasses mods
        // (e.g. eclipsescustomname) that inject their name by wrapping getName() *inside*
        // getDisplayName(), wiping the player's custom name. Nulling the cache forces a lazy
        // recompute through getDisplayName(), where those mods participate again — and where our
        // own onNameFormat handler still gets to prepend [LIVE].
        ((PlayerDisplayNameAccessor) player).lampas$setDisplayName(null);
        player.refreshTabListName();   // broadcasts UPDATE_DISPLAY_NAME to all clients
    }

    @SubscribeEvent
    public void onNameFormat(PlayerEvent.NameFormat event) {
        if (isLive(event.getEntity())) {
            event.setDisplayname(Component.empty().append(LIVE_PREFIX).append(event.getDisplayname()));
        }
    }

    @SubscribeEvent
    public void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (isLive(event.getEntity())) {
            Component base = event.getDisplayName();
            if (base == null) base = event.getEntity().getName();
            event.setDisplayName(Component.empty().append(LIVE_PREFIX).append(base));
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // State is loaded from NBT before this fires; refresh so a reconnecting live player
        // is shown as [LIVE] right away.
        if (event.getEntity() instanceof ServerPlayer player && isLive(player)) {
            refreshNames(player);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("live")
                .executes(ctx -> reportStatus(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("toggle").executes(ctx ->
                        apply(ctx.getSource().getPlayerOrException(), !isLive(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("on").executes(ctx ->
                        apply(ctx.getSource().getPlayerOrException(), true)))
                .then(Commands.literal("off").executes(ctx ->
                        apply(ctx.getSource().getPlayerOrException(), false)))
        );
    }

    private static int apply(ServerPlayer player, boolean live) {
        setLive(player, live);
        refreshNames(player);
        if (live) {
            player.sendSystemMessage(Component.literal("You are now marked as ")
                    .append(Component.literal("LIVE").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal(". A [LIVE] tag now shows next to your name.")));
        } else {
            player.sendSystemMessage(Component.literal("You are no longer marked as live.")
                    .withStyle(ChatFormatting.GRAY));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int reportStatus(ServerPlayer player) {
        boolean live = isLive(player);
        player.sendSystemMessage(Component.literal("Live status: ")
                .append(live
                        ? Component.literal("ON").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                        : Component.literal("OFF").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  (use /live toggle)").withStyle(ChatFormatting.DARK_GRAY)));
        return Command.SINGLE_SUCCESS;
    }
}
