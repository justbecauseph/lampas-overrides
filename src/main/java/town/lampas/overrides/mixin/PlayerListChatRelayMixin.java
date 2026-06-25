package town.lampas.overrides.mixin;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import town.lampas.overrides.ModConfig;
import town.lampas.overrides.chat.DiscordChatRelay;

import java.util.function.Predicate;

/**
 * Feeds the Discord chat relay from the two {@link PlayerList} broadcast chokepoints.
 *
 * <p>{@code broadcastChatMessage} carries everything delivered to all players as <em>chat</em>:
 * normal player chat (relayed under the player's display name + head), plus {@code /say} and
 * {@code /me} (relayed under the server identity). {@code broadcastSystemMessage} carries the
 * <em>system</em> broadcasts: joins, leaves, death messages and advancement announcements.
 *
 * <p>Private channels (<code>/msg</code>, team chat) and {@code /tellraw} do not pass through these
 * methods, so they are not captured here &mdash; {@code /tellraw} is handled separately in
 * {@code ChatRelayHandler} via the command event. Each handler does only cheap string work and a
 * non-blocking enqueue; all I/O happens on the relay's own thread.
 */
@Mixin(PlayerList.class)
public class PlayerListChatRelayMixin {

    @Inject(
            method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("HEAD")
    )
    private void lampas$relayChat(PlayerChatMessage message, Predicate<ServerPlayer> shouldFilter,
                                  ServerPlayer sender, ChatType.Bound boundChatType, CallbackInfo ci) {
        DiscordChatRelay relay = DiscordChatRelay.get();
        if (relay == null) {
            return;
        }
        String body = message.decoratedContent().getString();
        if (body.isEmpty()) {
            return;
        }
        if (boundChatType.chatType().is(ChatType.CHAT)) {
            if (sender != null) {
                relay.onPlayerChat(sender.getDisplayName().getString(), sender.getUUID(), body);
            }
        } else if (boundChatType.chatType().is(ChatType.SAY_COMMAND)) {
            if (ModConfig.DISCORD_RELAY_SAY_ME.get()) {
                relay.onServerMessage("[" + boundChatType.name().getString() + "] " + body);
            }
        } else if (boundChatType.chatType().is(ChatType.EMOTE_COMMAND)) {
            if (ModConfig.DISCORD_RELAY_SAY_ME.get()) {
                relay.onServerMessage("* " + boundChatType.name().getString() + " " + body);
            }
        }
    }

    @Inject(
            method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD")
    )
    private void lampas$relaySystem(Component message, boolean bypassHiddenChat, CallbackInfo ci) {
        DiscordChatRelay relay = DiscordChatRelay.get();
        if (relay == null || !ModConfig.DISCORD_RELAY_SYSTEM.get()) {
            return;
        }
        relay.onServerMessage(message.getString());
    }
}
