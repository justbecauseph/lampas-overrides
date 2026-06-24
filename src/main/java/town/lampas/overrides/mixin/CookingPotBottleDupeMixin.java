package town.lampas.overrides.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fixes a glass-bottle duplication exploit in Farmer's Delight cooking pots.
 *
 * <p>{@code CookingPotBlockEntity#processCooking} loops over every consumed ingredient and
 * ejects its crafting remainder ({@code if (stack.hasCraftingRemainingItem()) ejectIngredientRemainder(...)}).
 * Ube's Delight bottled drinks are built via its {@code drinkItem(...)} helper, which sets
 * {@code Item.Properties.craftRemainder(Items.GLASS_BOTTLE)}. So when a cooking recipe consumes
 * such a bottle (e.g. {@code ubesdelight:condensed_milk_bottle}) to produce an output that is
 * <em>itself</em> a bottled drink ({@code milk_tea_ube}, {@code condensed_milk_bottle}, ...), the
 * pot returns a free empty glass bottle on top of the bottled result &mdash; a net +1 glass bottle
 * every craft.
 *
 * <p>We wrap the remainder eject and skip it when both the remainder and the recipe result are
 * glass-bottle-based, since the bottle is already represented by the bottled output. Bowl desserts
 * such as {@code halo_halo} / {@code leche_flan} are unaffected: their result is built via
 * {@code bowlFoodItem(...)} so its remainder is a BOWL, not a glass bottle, and their (legitimate)
 * empty-bottle return is preserved.
 *
 * <p>Targeted by fully-qualified name so we take no compile/runtime dependency on Farmer's Delight;
 * the mixin is gated on {@code farmersdelight} being loaded in {@link LampasMixinPlugin}.
 */
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity", remap = false)
public class CookingPotBottleDupeMixin {

    @WrapOperation(
            method = "processCooking",
            at = @At(
                    value = "INVOKE",
                    target = "Lvectorwing/farmersdelight/common/block/entity/CookingPotBlockEntity;"
                            + "ejectIngredientRemainder(Lnet/minecraft/world/item/ItemStack;)V"
            )
    )
    private void lampas$noBottleDupe(Object self, ItemStack remainder, Operation<Void> original,
                                     @Local(ordinal = 0) ItemStack result) {
        if (remainder.is(Items.GLASS_BOTTLE)
                && !result.isEmpty()
                && result.hasCraftingRemainingItem()
                && result.getCraftingRemainingItem().is(Items.GLASS_BOTTLE)) {
            // Output is itself a bottled drink; the glass bottle is already in the result.
            return;
        }
        original.call(self, remainder);
    }
}
