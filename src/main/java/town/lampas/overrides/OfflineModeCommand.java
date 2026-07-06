package town.lampas.overrides;

import com.mojang.brigadier.Command;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Server-side admin toggle for {@link ModConfig#OFFLINE_MODE} via {@code /offlinemode}.
 *
 * <p>When on, every outbound HTTP call the mod makes is short-circuited (see
 * {@link HttpUtil#skipHttpCall(String)}), so it runs fully self-contained — offline, singleplayer, or
 * on a networkless box — with no request timeouts tying up async threads and no error-log spam.
 * Toggling it live via this command avoids a config reload; the change persists to the config file.
 */
public class OfflineModeCommand {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("offlinemode")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> reportStatus(ctx.getSource()))
                .then(Commands.literal("toggle").executes(ctx ->
                        apply(ctx.getSource(), !ModConfig.OFFLINE_MODE.get())))
                .then(Commands.literal("on").executes(ctx ->
                        apply(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx ->
                        apply(ctx.getSource(), false)))
        );
    }

    private static int apply(CommandSourceStack source, boolean enabled) {
        ModConfig.OFFLINE_MODE.set(enabled);
        ModConfig.OFFLINE_MODE.save();
        source.sendSuccess(() -> Component.literal("Offline mode is now ")
                .append(enabled
                        ? Component.literal("ON").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        : Component.literal("OFF").withStyle(ChatFormatting.GREEN))
                .append(enabled
                        ? Component.literal("  (portal HTTP calls are now skipped)").withStyle(ChatFormatting.DARK_GRAY)
                        : Component.literal("  (portal HTTP calls resume)").withStyle(ChatFormatting.DARK_GRAY)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int reportStatus(CommandSourceStack source) {
        boolean enabled = ModConfig.OFFLINE_MODE.get();
        source.sendSuccess(() -> Component.literal("Offline mode: ")
                .append(enabled
                        ? Component.literal("ON").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        : Component.literal("OFF").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("  (use /offlinemode toggle)").withStyle(ChatFormatting.DARK_GRAY)), false);
        return Command.SINGLE_SUCCESS;
    }
}
