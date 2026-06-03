package town.lampas.overrides.mixin;

import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.common.bank.BankAccount;
import io.github.lightman314.lightmanscurrency.common.items.cards.ATMCardMoneyHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import town.lampas.overrides.BankAccountPlayerExtension;
import town.lampas.overrides.BankSyncHandler;

import java.util.UUID;

@Mixin(value = ATMCardMoneyHandler.class, remap = false)
public abstract class ATMCardMoneyHandlerMixin {

    @Shadow
    protected abstract IBankAccount getAccount();

    @Inject(method = "extractMoney", at = @At("RETURN"))
    private void onExtractMoneyReturn(MoneyValue extractAmount, boolean simulation, CallbackInfoReturnable<MoneyValue> cir) {
        if (!simulation) {
            MoneyValue remainder = cir.getReturnValue();
            if (remainder != null) {
                MoneyValue amountTaken = extractAmount.subtractValue(remainder);
                if (amountTaken != null && !amountTaken.isEmpty()) {
                    IBankAccount account = this.getAccount();
                    if (account instanceof BankAccount bankAccount && bankAccount instanceof BankAccountPlayerExtension ext) {
                        UUID playerUUID = ext.lampasOverrides$getPlayerUUID();
                        if (playerUUID != null && !bankAccount.isClient()) {
                            BankSyncHandler.handleBankBalanceChange(playerUUID, bankAccount, amountTaken, "withdrawal");
                        }
                    }
                }
            }
        }
    }
}
