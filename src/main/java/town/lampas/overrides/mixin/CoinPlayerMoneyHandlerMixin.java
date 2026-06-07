package town.lampas.overrides.mixin;

import io.github.lightman314.lightmanscurrency.api.capability.money.IMoneyHandler;
import io.github.lightman314.lightmanscurrency.api.capability.money.CapabilityMoneyHandler;
import io.github.lightman314.lightmanscurrency.api.money.types.builtin.coins.CoinPlayerMoneyHandler;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyView;
import io.github.lightman314.lightmanscurrency.common.attachments.WalletHandler;
import io.github.lightman314.lightmanscurrency.common.items.WalletItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;

@Mixin(value = CoinPlayerMoneyHandler.class, remap = false)
public abstract class CoinPlayerMoneyHandlerMixin {

    @Shadow
    private WalletHandler walletHandler;

    @Shadow
    private boolean requireWallet;

    @Unique
    private boolean lampasOverrides$allowInteraction() {
        return this.walletHandler != null && (!this.requireWallet || WalletItem.isWallet(this.walletHandler.getWallet()));
    }

    @Inject(method = "collectStoredMoney", at = @At("TAIL"))
    private void onCollectStoredMoney(MoneyView.Builder builder, CallbackInfo ci) {
        if (town.lampas.overrides.PlayerActivityListener.IS_MERGING_INVENTORY.get()) {
            return;
        }
        try {
            town.lampas.overrides.PlayerActivityListener.IS_MERGING_INVENTORY.set(true);
            if (this.walletHandler != null && this.walletHandler.entity() instanceof Player player) {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.isEmpty()) {
                        IMoneyHandler itemHandler = stack.getCapability(CapabilityMoneyHandler.MONEY_HANDLER_ITEM);
                        if (itemHandler != null) {
                            builder.merge(itemHandler.getStoredMoney());
                        }
                    }
                }
            }
        } finally {
            town.lampas.overrides.PlayerActivityListener.IS_MERGING_INVENTORY.set(false);
        }
    }

    @Inject(method = "extractMoney", at = @At("HEAD"), cancellable = true)
    private void onExtractMoney(MoneyValue extractAmount, boolean simulation, CallbackInfoReturnable<MoneyValue> cir) {
        MoneyValue remaining = extractAmount;
        if (this.walletHandler != null && this.lampasOverrides$allowInteraction()) {
            remaining = this.walletHandler.extractMoney(remaining, simulation);
        }
        if (!remaining.isEmpty() && this.walletHandler != null && this.walletHandler.entity() instanceof Player player) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    IMoneyHandler itemHandler = stack.getCapability(CapabilityMoneyHandler.MONEY_HANDLER_ITEM);
                    if (itemHandler != null) {
                        remaining = itemHandler.extractMoney(remaining, simulation);
                        if (remaining.isEmpty()) {
                            break;
                        }
                    }
                }
            }
        }
        cir.setReturnValue(remaining);
    }

    @Inject(method = "insertMoney", at = @At("HEAD"), cancellable = true)
    private void onInsertMoney(MoneyValue insertAmount, boolean simulation, CallbackInfoReturnable<MoneyValue> cir) {
        MoneyValue remaining = insertAmount;
        if (this.walletHandler != null && this.lampasOverrides$allowInteraction()) {
            remaining = this.walletHandler.insertMoney(remaining, simulation);
        }
        if (!remaining.isEmpty() && this.walletHandler != null && this.walletHandler.entity() instanceof Player player) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    IMoneyHandler itemHandler = stack.getCapability(CapabilityMoneyHandler.MONEY_HANDLER_ITEM);
                    if (itemHandler != null) {
                        remaining = itemHandler.insertMoney(remaining, simulation);
                        if (remaining.isEmpty()) {
                            break;
                        }
                    }
                }
            }
        }
        cir.setReturnValue(remaining);
    }
}
