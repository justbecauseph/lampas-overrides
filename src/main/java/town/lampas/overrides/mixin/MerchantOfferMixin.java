package town.lampas.overrides.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(MerchantOffer.class)
public class MerchantOfferMixin {

    @Shadow
    @Mutable
    private ItemCost baseCostA;

    @Shadow
    @Mutable
    private Optional<ItemCost> costB;

    @Shadow
    @Mutable
    private ItemStack result;

    private static final ResourceLocation COIN_EMERALD_ID = ResourceLocation.fromNamespaceAndPath("lightmanscurrency", "coin_emerald");

    @Inject(method = "<init>(Lnet/minecraft/world/item/trading/ItemCost;Ljava/util/Optional;Lnet/minecraft/world/item/ItemStack;IIIFI)V", at = @At("RETURN"))
    private void onInit(ItemCost baseCostA, Optional<ItemCost> costB, ItemStack result, int uses, int maxUses, int xp, float priceMultiplier, int demand, CallbackInfo ci) {
        Item coin = BuiltInRegistries.ITEM.get(COIN_EMERALD_ID);
        if (coin == Items.AIR || coin == null) {
            return;
        }

        if (this.baseCostA.item().value() == Items.EMERALD) {
            this.baseCostA = new ItemCost(BuiltInRegistries.ITEM.wrapAsHolder(coin), this.baseCostA.count(), this.baseCostA.components());
        }

        if (this.costB.isPresent()) {
            ItemCost b = this.costB.get();
            if (b.item().value() == Items.EMERALD) {
                this.costB = Optional.of(new ItemCost(BuiltInRegistries.ITEM.wrapAsHolder(coin), b.count(), b.components()));
            }
        }

        if (this.result.getItem() == Items.EMERALD) {
            this.result = this.result.transmuteCopy(coin, this.result.getCount());
        }
    }
}
