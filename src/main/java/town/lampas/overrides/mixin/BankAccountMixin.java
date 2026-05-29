package town.lampas.overrides.mixin;

import io.github.lightman314.lightmanscurrency.common.bank.BankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import town.lampas.overrides.BankAccountPlayerExtension;
import town.lampas.overrides.BankSyncHandler;

import java.util.UUID;

@Mixin(value = BankAccount.class, remap = false)
public class BankAccountMixin implements BankAccountPlayerExtension {

    @Unique
    private UUID lampasOverrides$playerUUID;

    @Override
    public UUID lampasOverrides$getPlayerUUID() {
        return this.lampasOverrides$playerUUID;
    }

    @Override
    public void lampasOverrides$setPlayerUUID(UUID uuid) {
        this.lampasOverrides$playerUUID = uuid;
    }

    @Inject(method = "depositMoney", at = @At("RETURN"))
    private void onDepositMoney(MoneyValue depositAmount, CallbackInfo ci) {
        if (this.lampasOverrides$playerUUID != null) {
            BankAccount account = (BankAccount) (Object) this;
            if (!account.isClient()) {
                BankSyncHandler.handleBankBalanceChange(this.lampasOverrides$playerUUID, account, depositAmount, "deposit");
            }
        }
    }

    @Inject(method = "withdrawMoney", at = @At("RETURN"))
    private void onWithdrawMoney(MoneyValue withdrawAmount, CallbackInfoReturnable<MoneyValue> cir) {
        if (this.lampasOverrides$playerUUID != null) {
            BankAccount account = (BankAccount) (Object) this;
            if (!account.isClient()) {
                MoneyValue actualWithdrawn = cir.getReturnValue();
                if (actualWithdrawn != null && !actualWithdrawn.isEmpty()) {
                    BankSyncHandler.handleBankBalanceChange(this.lampasOverrides$playerUUID, account, actualWithdrawn, "withdrawal");
                }
            }
        }
    }
}
