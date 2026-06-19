package town.lampas.overrides.mixin;

import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.common.taxes.TaxEntry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import town.lampas.overrides.ModConfig;

@Mixin(value = TaxEntry.class, remap = false)
public class TaxEntryMixin {

    @Shadow
    private boolean forceAcceptance;

    @Inject(method = "<init>(JLnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/player/Player;)V", at = @At("RETURN"))
    private void onInit(long id, BlockEntity core, Player owner, CallbackInfo ci) {
        if (ModConfig.TAX_COLLECTOR_FORCE_ACCEPTANCE.get()) {
            this.forceAcceptance = true;
        }
    }

    /**
     * The vanilla tax calculation rounds the percentage down, so a small tax on a cheap trade
     * (e.g. 5% of 5 Aur = 0.25) truncates to zero and nothing is ever collected. When the minimum
     * tax option is enabled, round up instead so any genuinely taxed trade collects at least the
     * smallest coin (1 Aur).
     */
    @Redirect(
            method = "CalculateAndPayTaxes",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/github/lightman314/lightmanscurrency/api/money/value/MoneyValue;percentageOfValue(I)Lio/github/lightman314/lightmanscurrency/api/money/value/MoneyValue;"
            )
    )
    private MoneyValue lampas$enforceMinimumTax(MoneyValue taxableAmount, int percentage) {
        MoneyValue tax = taxableAmount.percentageOfValue(percentage);
        if (ModConfig.TAX_COLLECTOR_MINIMUM_TAX.get() && tax.isEmpty() && percentage > 0 && !taxableAmount.isEmpty()) {
            tax = taxableAmount.percentageOfValue(percentage, true);
        }
        return tax;
    }

}
