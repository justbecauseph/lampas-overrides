package town.lampas.overrides.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.satisfy.wildernature.core.block.BountyBoardBlock;

import town.lampas.overrides.BountyApiFetcher;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.server.network.Filterable;

import java.util.ArrayList;
import java.util.List;

@Mixin(BountyBoardBlock.class)
public class BountyBoardBlockMixin {

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void onUseWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (world.isClientSide()) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("Loading bounties...").withStyle(ChatFormatting.YELLOW));
            
            BountyApiFetcher.getBountiesAsync(bounties -> {
                serverPlayer.server.execute(() -> {
                    openBountyBoardBook(serverPlayer, bounties);
                });
            });
        }
        
        cir.setReturnValue(InteractionResult.CONSUME);
    }

    private void openBountyBoardBook(ServerPlayer player, List<BountyApiFetcher.Bounty> bounties) {
        List<Filterable<Component>> pages = new ArrayList<>();

        // Page 1: Welcome/Index
        net.minecraft.network.chat.MutableComponent welcome = Component.literal("=== BOUNTY BOARD ===\n\n").withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD);
        welcome.append(Component.literal("Flip the pages to view active contracts.\n\n").withStyle(ChatFormatting.BLACK));
        welcome.append(Component.literal("Click ").withStyle(ChatFormatting.BLACK));
        welcome.append(Component.literal("[ACCEPT CONTRACT]").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        welcome.append(Component.literal(" on any page to claim it.\n\n").withStyle(ChatFormatting.BLACK));
        welcome.append(Component.literal("Available today: " + bounties.size()).withStyle(ChatFormatting.DARK_BLUE));
        pages.add(Filterable.passThrough(welcome));

        // Add a page for each bounty
        for (BountyApiFetcher.Bounty bounty : bounties) {
            net.minecraft.network.chat.MutableComponent page = Component.literal("=== CONTRACT ===\n\n").withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD);
            page.append(Component.literal("Title: ").withStyle(ChatFormatting.DARK_GRAY));
            page.append(Component.literal(bounty.title() + "\n\n").withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.BOLD));

            page.append(Component.literal("Objective:\n").withStyle(ChatFormatting.DARK_GRAY));
            page.append(Component.literal(bounty.description() + "\n\n").withStyle(ChatFormatting.ITALIC, ChatFormatting.BLACK));

            page.append(Component.literal("Reward: ").withStyle(ChatFormatting.DARK_GRAY));
            page.append(Component.literal(bounty.prizeAmount() + " " + bounty.prizeType() + "\n\n").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD));

            if (bounty.isClaimedBy(player.getUUID())) {
                page.append(Component.literal("[ ALREADY CLAIMED ]").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            } else {
                page.append(Component.literal("[ ACCEPT CONTRACT ]")
                    .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claimbounty " + bounty.id()))
                    )
                );
            }
            pages.add(Filterable.passThrough(page));
        }

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
            Filterable.passThrough("Bounty Board"),
            "Bounty Board",
            0,
            pages,
            true
        ));

        // Swapping trick
        ItemStack originalItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        
        // Update slot on client
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
            0, player.inventoryMenu.getStateId(), 36 + player.getInventory().selected, book
        ));
        
        // Use NeoForge openItemGui method to open the book UI
        player.openItemGui(book, InteractionHand.MAIN_HAND);
        
        // Restore hand on server
        player.setItemInHand(InteractionHand.MAIN_HAND, originalItem);
        
        // Update slot on client back to original
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
            0, player.inventoryMenu.getStateId(), 36 + player.getInventory().selected, originalItem
        ));
    }
}
