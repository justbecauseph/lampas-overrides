package town.lampas.overrides.mixin;

import io.github.lightman314.lightmanscurrency.common.data.types.BankDataCache;
import io.github.lightman314.lightmanscurrency.common.bank.BankAccount;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import town.lampas.overrides.BankAccountPlayerExtension;

import java.util.UUID;

@Mixin(value = BankDataCache.class, remap = false)
public class BankDataCacheMixin {

    @Inject(method = "loadBankAccount", at = @At("RETURN"))
    private void onLoadBankAccount(UUID player, CompoundTag compound, HolderLookup.Provider lookup, CallbackInfoReturnable<BankAccount> cir) {
        BankAccount bankAccount = cir.getReturnValue();
        if (bankAccount instanceof BankAccountPlayerExtension ext) {
            ext.lampasOverrides$setPlayerUUID(player);
        }
    }

    @Inject(method = "generateBankAccount", at = @At("RETURN"))
    private void onGenerateBankAccount(UUID player, CallbackInfoReturnable<BankAccount> cir) {
        BankAccount bankAccount = cir.getReturnValue();
        if (bankAccount instanceof BankAccountPlayerExtension ext) {
            ext.lampasOverrides$setPlayerUUID(player);
        }
    }
}
