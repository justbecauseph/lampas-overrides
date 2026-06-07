package town.lampas.overrides.mixin;

import io.github.lightman314.lightmanscurrency.common.attachments.wallet.WalletHelpers;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyView;
import io.github.lightman314.lightmanscurrency.api.capability.money.IMoneyHandler;
import io.github.lightman314.lightmanscurrency.api.capability.money.CapabilityMoneyHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;

@Mixin(value = WalletHelpers.class, remap = false)
public class WalletHelpersMixin {

    @Inject(method = "getWalletMoney", at = @At("RETURN"), cancellable = true)
    private static void onGetWalletMoney(@Nonnull final LivingEntity entity, CallbackInfoReturnable<MoneyView> cir) {
        if (entity instanceof Player player) {
            if (town.lampas.overrides.PlayerActivityListener.IS_MERGING_INVENTORY.get()) {
                return;
            }
            try {
                town.lampas.overrides.PlayerActivityListener.IS_MERGING_INVENTORY.set(true);
                MoneyView original = cir.getReturnValue();
                MoneyView.Builder builder = MoneyView.builder().merge(original);
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.isEmpty()) {
                        IMoneyHandler itemHandler = stack.getCapability(CapabilityMoneyHandler.MONEY_HANDLER_ITEM);
                        if (itemHandler != null) {
                            builder.merge(itemHandler.getStoredMoney());
                        }
                    }
                }
                cir.setReturnValue(builder.build());
            } finally {
                town.lampas.overrides.PlayerActivityListener.IS_MERGING_INVENTORY.set(false);
            }
        }
    }
}
