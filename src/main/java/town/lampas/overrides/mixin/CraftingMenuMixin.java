package town.lampas.overrides.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingMenu.class)
public class CraftingMenuMixin {

    @Inject(
        method = "slotChangedCraftingGrid(Lnet/minecraft/world/inventory/AbstractContainerMenu;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/inventory/ResultContainer;Lnet/minecraft/world/item/crafting/RecipeHolder;)V",
        at = @At("TAIL")
    )
    private static void onSlotChangedCraftingGrid(
        AbstractContainerMenu menu,
        Level level,
        Player player,
        CraftingContainer craftSlots,
        ResultContainer resultSlots,
        RecipeHolder<CraftingRecipe> recipe,
        CallbackInfo ci
    ) {
        if (!level.isClientSide) {
            ItemStack result = resultSlots.getItem(0);
            if (result != null && !result.isEmpty()) {
                if (isOreProcessingFurnace(result)) {
                    // Check if player is a normal player (i.e. not OP / permission level < 2)
                    if (player != null && !player.hasPermissions(2)) {
                        resultSlots.setItem(0, ItemStack.EMPTY);
                        menu.setRemoteSlot(0, ItemStack.EMPTY);
                        if (player instanceof ServerPlayer serverPlayer) {
                            serverPlayer.connection.send(
                                new ClientboundContainerSetSlotPacket(
                                    menu.containerId,
                                    menu.incrementStateId(),
                                    0,
                                    ItemStack.EMPTY
                                )
                            );
                        }
                    }
                }
            }
        }
    }

    private static boolean isOreProcessingFurnace(ItemStack stack) {
        if (stack.is(Items.FURNACE) || stack.is(Items.BLAST_FURNACE)) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            String path = id.getPath().toLowerCase();
            // Block anything containing "furnace" but not minecarts
            if (path.contains("furnace") && !path.contains("minecart")) {
                return true;
            }
        }
        return false;
    }
}
