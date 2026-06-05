package town.lampas.overrides.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@Mixin(targets = "net.mcreator.neoncraft.procedures.NeonBlockColorProcedure", remap = false)
public class NeonBlockColorProcedureMixin {

    @Inject(method = "onRightClickBlock", at = @At("HEAD"), cancellable = true)
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event, CallbackInfo ci) {
        ci.cancel();
    }
}
