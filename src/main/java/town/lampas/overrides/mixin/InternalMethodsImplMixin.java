package town.lampas.overrides.mixin;

import net.blay09.mods.waystones.InternalMethodsImpl;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.blay09.mods.waystones.requirement.NoRequirement;
import net.blay09.mods.waystones.requirement.RefuseRequirement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import town.lampas.overrides.Faction;
import town.lampas.overrides.PlayerActivityListener;

@Mixin(value = InternalMethodsImpl.class, remap = false)
public class InternalMethodsImplMixin {

    @Inject(
        method = "resolveRequirements(Lnet/blay09/mods/waystones/api/WaystoneTeleportContext;)Lnet/blay09/mods/waystones/api/requirement/WarpRequirement;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onResolveRequirements(WaystoneTeleportContext context, CallbackInfoReturnable<WarpRequirement> cir) {
        if (context.getEntity() instanceof Player player) {
            if (!player.level().isClientSide && !player.hasPermissions(2)) {
                ResourceLocation targetType = context.getTargetWaystone().getWaystoneType();
                if (targetType != null && targetType.getNamespace().equals("waystones")) {
                    Faction requiredFaction = PlayerActivityListener.getRequiredFactionForSharestone(targetType.getPath());
                    if (requiredFaction != Faction.NONE) {
                        Faction playerFaction = PlayerActivityListener.getPlayerFaction(player.getUUID());
                        if (playerFaction != requiredFaction) {
                            cir.setReturnValue(new RefuseRequirement(
                                Component.literal("You must belong to the " + requiredFaction.name() + " faction to use this sharestone.")
                            ));
                            return;
                        }
                    }
                }
            }
        }

        if (context.getFromWaystone().isPresent()
            && context.getFromWaystone().get().getWaystoneType().getPath().endsWith("_sharestone")
            && context.getTargetWaystone().getWaystoneType().getPath().endsWith("_sharestone")) {
            cir.setReturnValue(NoRequirement.INSTANCE);
        }
    }
}

