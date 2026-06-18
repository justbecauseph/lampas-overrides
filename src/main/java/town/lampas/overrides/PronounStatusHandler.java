package town.lampas.overrides;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import town.lampas.overrides.mixin.PlayerDisplayNameAccessor;

import java.util.UUID;

/**
 * Server-side pronoun tag. On join, each player's pronouns are fetched from PronounDB (by Minecraft
 * UUID, see {@link PronounCache}) and a grey {@code [he/him]} prefix is prepended to their display
 * name for everyone — in chat, the tab list, nametags and Jade.
 *
 * <p>Chat picks up the prefix because the chat sender name is derived from
 * {@link Player#getDisplayName()} (via {@link PlayerEvent.NameFormat}); the tab list uses
 * {@link ServerPlayer#getTabListDisplayName()} (via {@link PlayerEvent.TabListNameFormat}). This
 * mirrors {@link LiveStatusHandler}; the two prefixes stack ({@code [LIVE] [he/him] Name}).
 */
public class PronounStatusHandler {

    private static Component prefixFor(Player player) {
        String pronouns = PronounCache.get(player.getUUID());
        if (pronouns == null) return null;
        return Component.literal("[" + pronouns + "] ").withStyle(ChatFormatting.GRAY);
    }

    /**
     * Re-broadcasts the player's name to clients so the pronoun prefix appears once the async fetch
     * resolves. Nulls the cached display name (rather than {@code refreshDisplayName()}) so mods that
     * inject their name inside {@code getDisplayName()} still participate — see
     * {@link LiveStatusHandler#refreshNames}.
     */
    private static void refreshNames(ServerPlayer player) {
        ((PlayerDisplayNameAccessor) player).lampas$setDisplayName(null);
        player.refreshTabListName();
    }

    @SubscribeEvent
    public void onNameFormat(PlayerEvent.NameFormat event) {
        Component prefix = prefixFor(event.getEntity());
        if (prefix != null) {
            event.setDisplayname(Component.empty().append(prefix).append(event.getDisplayname()));
        }
    }

    @SubscribeEvent
    public void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        Component prefix = prefixFor(event.getEntity());
        if (prefix != null) {
            Component base = event.getDisplayName();
            if (base == null) base = event.getEntity().getName();
            event.setDisplayName(Component.empty().append(prefix).append(base));
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        // Warm the cache; when the pronouns land, hop back to the server thread and refresh the
        // name so the prefix shows up without the player having to relog.
        PronounCache.prefetch(uuid, () -> {
            if (!player.hasDisconnected()) {
                player.server.execute(() -> {
                    if (!player.hasDisconnected()) refreshNames(player);
                });
            }
        });
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PronounCache.forget(player.getUUID());
        }
    }
}
