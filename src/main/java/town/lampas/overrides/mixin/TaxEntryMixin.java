package town.lampas.overrides.mixin;

import io.github.lightman314.lightmanscurrency.common.taxes.TaxEntry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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

}
