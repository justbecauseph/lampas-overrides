package town.lampas.overrides.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import town.lampas.overrides.Faction;
import town.lampas.overrides.PlayerActivityListener;

@Mixin(FishingHook.class)
public class FishingHookMixin {

    @Shadow
    @Final
    @Mutable
    private int luck;

    @Shadow
    @Final
    @Mutable
    private int lureSpeed;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V", at = @At("RETURN"))
    private void onInit(Player player, Level level, int luck, int lureSpeed, CallbackInfo ci) {
        if (!level.isClientSide && player != null) {
            Faction faction = PlayerActivityListener.getPlayerFaction(player.getUUID());
            if (faction == Faction.FISHERIES) {
                // Boost fishing speed (+100 ticks = 5s faster bite time, equivalent to +1 level of Lure)
                this.lureSpeed += 100;
                
                // Boost fishing luck (+2 luck, equivalent to +2 levels of Luck of the Sea)
                this.luck += 2;
            }
        }
    }
}
