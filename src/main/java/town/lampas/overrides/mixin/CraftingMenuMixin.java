package town.lampas.overrides.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import town.lampas.overrides.Faction;
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
                // Check if player is a normal player (i.e. not OP / permission level < 2)
                if (player != null && !player.hasPermissions(2)) {
                    if (isBlockedCraft(result, player)) {
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

    private static boolean isBlockedCraft(ItemStack stack, Player player) {
        if (player == null) return false;

        // Determine restriction category BEFORE looking up faction (avoids unnecessary API/cache hits)
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());

        boolean isFurnace = stack.is(Items.FURNACE) || stack.is(Items.BLAST_FURNACE);
        if (!isFurnace && id != null) {
            String path = id.getPath();
            if (path.contains("furnace") && !path.contains("minecart")) {
                isFurnace = true;
            }
        }

        if (isFurnace) {
            return town.lampas.overrides.PlayerActivityListener.getPlayerFaction(player.getUUID()) != Faction.LMI;
        }

        if (stack.is(Items.BREWING_STAND) || stack.is(Items.SMOKER)) {
            return town.lampas.overrides.PlayerActivityListener.getPlayerFaction(player.getUUID()) != Faction.FISHERIES;
        }

        if (id != null && id.getNamespace().equals("lightmanscurrency")) {
            if (id.getPath().equals("tax_block")) {
                return town.lampas.overrides.PlayerActivityListener.getPlayerFaction(player.getUUID()) != Faction.NOBILITY;
            }
            if (id.getPath().equals("vending_machine_large_black")) {
                return town.lampas.overrides.PlayerActivityListener.getPlayerFaction(player.getUUID()) != Faction.MERCHANTS;
            }
        }

        // Combat restriction: only MERCHANTS are blocked from crafting combat items
        if (isCombatItem(stack)) {
            return town.lampas.overrides.PlayerActivityListener.getPlayerFaction(player.getUUID()) == Faction.MERCHANTS;
        }

        return false;
    }

    private static boolean isCombatItem(ItemStack stack) {
        net.minecraft.world.item.Item item = stack.getItem();
        return item instanceof net.minecraft.world.item.SwordItem
            || item instanceof net.minecraft.world.item.BowItem
            || item instanceof net.minecraft.world.item.CrossbowItem
            || item instanceof net.minecraft.world.item.TridentItem
            || item instanceof net.minecraft.world.item.MaceItem;
    }
}
