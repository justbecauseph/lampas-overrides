package town.lampas.overrides.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import town.lampas.overrides.PlayerLadderHandler;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "removePassenger", at = @At("TAIL"))
    private void playerladder$removePassenger(Entity passenger, CallbackInfo ci) {
        PlayerLadderHandler.onDismount((Entity) (Object) this);
    }

    @Inject(method = "addPassenger", at = @At("TAIL"))
    private void playerladder$onAddPassenger(Entity passenger, CallbackInfo ci) {
        PlayerLadderHandler.onMount((Entity) (Object) this, passenger);
    }

    // --- Liquid Fertilizer (Create: Slice & Dice) hurts like lava ---
    private static final ResourceLocation FERTILIZER$FLUID_ID =
            ResourceLocation.fromNamespaceAndPath("sliceanddice", "fertilizer");
    private static final ResourceKey<DamageType> FERTILIZER$DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("lampas_overrides", "liquid_fertilizer"));
    private static final float FERTILIZER$DAMAGE = 4.0F;

    // Resolved lazily; stays null if Slice & Dice isn't loaded (registry lookup returns EMPTY).
    private static FluidType fertilizer$fluidType;
    private static boolean fertilizer$resolved;

    private static FluidType fertilizer$fluidType() {
        if (!fertilizer$resolved) {
            fertilizer$resolved = true;
            var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID
                    .getOptional(FERTILIZER$FLUID_ID).orElse(null);
            if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                fertilizer$fluidType = fluid.getFluidType();
            }
        }
        return fertilizer$fluidType;
    }

    @Inject(method = "baseTick", at = @At("TAIL"))
    private void fertilizer$hurtInFertilizer(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Level level = self.level();
        if (level.isClientSide) {
            return;
        }
        FluidType type = fertilizer$fluidType();
        if (type == null || self.getFluidTypeHeight(type) <= 0.0) {
            return;
        }
        // hurt()'s own invulnerability frames rate-limit this to ~every 0.5s, exactly like lava.
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(FERTILIZER$DAMAGE_TYPE);
        self.hurt(new DamageSource(holder), FERTILIZER$DAMAGE);
    }

}
