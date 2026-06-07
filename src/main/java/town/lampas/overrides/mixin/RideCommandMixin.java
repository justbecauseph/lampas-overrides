package town.lampas.overrides.mixin;

import net.minecraft.server.commands.RideCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import town.lampas.overrides.ModConfig;

@Mixin(RideCommand.class)
public class RideCommandMixin {

    @Redirect(method = "mount",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getType()Lnet/minecraft/world/entity/EntityType;"))
    private static EntityType<?> playerladder$rideExtension(Entity entity) {
        return ModConfig.LADDER_RIDE_EXTENSION.get() ? null : entity.getType();
    }
}
