package town.lampas.overrides.mixin;

import net.minecraft.world.effect.MobEffectInstance;
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
import town.lampas.overrides.ModEffects;
import town.lampas.overrides.PlayerActivityListener;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin {

    @Shadow
    @Final
    @Mutable
    private int luck;

    @Shadow
    @Final
    @Mutable
    private int lureSpeed;

    @Shadow
    public abstract Player getPlayerOwner();

    @Inject(method = "<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V", at = @At("RETURN"))
    private void onInit(Player player, Level level, int luck, int lureSpeed, CallbackInfo ci) {
        if (!level.isClientSide && player != null) {
            Faction faction = PlayerActivityListener.getPlayerFaction(player.getUUID());
            if (faction == Faction.FISHERIES) {
                // Boost fishing speed (+200 ticks = 10s faster bite time, equivalent to +2 levels of Lure)
                this.lureSpeed += 200;
                
                // Boost fishing luck (+2 luck, equivalent to +2 levels of Luck of the Sea)
                this.luck += 2;
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        FishingHook hook = (FishingHook) (Object) this;
        if (!hook.level().isClientSide) {
            Player player = this.getPlayerOwner();
            if (player != null) {
                Faction faction = PlayerActivityListener.getPlayerFaction(player.getUUID());
                if (faction == Faction.FISHERIES) {
                    MobEffectInstance activeBoon = player.getEffect(ModEffects.FISHERIES_BOON);
                    if (activeBoon == null || activeBoon.getDuration() <= 20) {
                        player.addEffect(new MobEffectInstance(
                                ModEffects.FISHERIES_BOON,
                                100, // 100 ticks = 5s duration
                                0,  // amplifier
                                true, // ambient
                                false, // visibleParticles
                                true   // showIcon
                        ));
                    }
                }
            }
        }
    }
}
