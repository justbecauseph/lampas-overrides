package town.lampas.overrides.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import town.lampas.overrides.ModConfig;

import java.util.function.Predicate;

@Mixin(ProjectileUtil.class)
public final class ProjectileUtilMixin {

    @ModifyVariable(
        method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
        at = @At("HEAD"),
        argsOnly = true
    )
    private static Predicate<Entity> playerladder$filterPassengers(Predicate<Entity> predicate, Entity shooter) {
        if (ModConfig.LADDER_ALLOW_INTERACTIONS.get()) {
            return entity -> predicate.test(entity) && !playerladder$isPassengerRecursive(shooter, entity);
        }
        return predicate;
    }

    private static boolean playerladder$isPassengerRecursive(Entity shooter, Entity entity) {
        Entity vehicle = entity.getVehicle();
        while (vehicle != null) {
            if (vehicle == shooter) {
                return true;
            }
            vehicle = vehicle.getVehicle();
        }
        return false;
    }
}

