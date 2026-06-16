package town.lampas.overrides;

import com.mojang.brigadier.Command;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Server-side admin toggle for {@link ModConfig#PLAGUE_MODE} via {@code /plaguemode}. */
public class PlagueModeCommand {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("plaguemode")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> reportStatus(ctx.getSource()))
                .then(Commands.literal("toggle").executes(ctx ->
                        apply(ctx.getSource(), !ModConfig.PLAGUE_MODE.get())))
                .then(Commands.literal("on").executes(ctx ->
                        apply(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx ->
                        apply(ctx.getSource(), false)))
        );
    }

    private static int apply(CommandSourceStack source, boolean enabled) {
        ModConfig.PLAGUE_MODE.set(enabled);
        ModConfig.PLAGUE_MODE.save();
        source.sendSuccess(() -> Component.literal("Plague mode is now ")
                .append(enabled
                        ? Component.literal("ON").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                        : Component.literal("OFF").withStyle(ChatFormatting.GRAY)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int reportStatus(CommandSourceStack source) {
        boolean enabled = ModConfig.PLAGUE_MODE.get();
        source.sendSuccess(() -> Component.literal("Plague mode: ")
                .append(enabled
                        ? Component.literal("ON").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                        : Component.literal("OFF").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  (use /plaguemode toggle)").withStyle(ChatFormatting.DARK_GRAY)), false);
        return Command.SINGLE_SUCCESS;
    }
}
