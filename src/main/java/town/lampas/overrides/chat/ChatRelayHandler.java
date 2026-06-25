package town.lampas.overrides.chat;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import town.lampas.overrides.ModConfig;

import java.util.List;

/**
 * Wires the Discord chat relay into the server lifecycle and captures {@code /tellraw}.
 *
 * <p>Player chat, {@code /say} and {@code /me} are captured at the {@code PlayerList} broadcast
 * chokepoints by {@code PlayerListChatRelayMixin}; {@code /tellraw} is the one global path that is
 * delivered per-player (so it touches no broadcast method), so it is intercepted here at the command
 * layer instead. {@link CommandEvent} fires in {@code Commands.performCommand}, which covers
 * {@code /tellraw} typed by a player, run from a command block, or from the server console. Commands
 * executed inside a datapack {@code .mcfunction} run through a different path and do <em>not</em> fire
 * this event, so function-issued {@code /tellraw} is not relayed.
 */
public class ChatRelayHandler {

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        DiscordChatRelay.init();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        DiscordChatRelay.shutdown();
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        DiscordChatRelay relay = DiscordChatRelay.get();
        if (relay == null || !ModConfig.DISCORD_RELAY_TELLRAW.get()) {
            return;
        }
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        List<ParsedCommandNode<CommandSourceStack>> nodes = parse.getContext().getNodes();
        if (nodes.isEmpty() || !"tellraw".equals(nodes.get(0).getNode().getName())) {
            return;
        }
        CommandSourceStack source = parse.getContext().getSource();
        // tellraw requires permission level 2; don't relay a command the source can't actually run.
        if (!source.hasPermission(2)) {
            return;
        }
        try {
            CommandContext<CommandSourceStack> ctx = parse.getContext().build(parse.getReader().getString());
            Component message = ComponentArgument.getComponent(ctx, "message");
            Component resolved = ComponentUtils.updateForEntity(source, message, source.getEntity(), 0);
            relay.onServerMessage(resolved.getString());
        } catch (Exception ignored) {
            // Not a well-formed tellraw (missing/!valid message arg, or selector resolution failed);
            // nothing to relay.
        }
    }
}
