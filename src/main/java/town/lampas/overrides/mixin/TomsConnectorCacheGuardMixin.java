package town.lampas.overrides.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes Tom's Simple Storage's inventory connector tolerant of an invalidated NeoForge
 * {@link BlockCapabilityCache} instead of crashing the server thread.
 *
 * <p>{@code PlatformInventoryAccess$BlockInventoryAccess#get()} returns
 * {@code itemCache.getCapability()}. On NeoForge 21.1.x, {@code BlockCapabilityCache#getCapability()}
 * <em>throws</em> {@code IllegalStateException("Do not call getCapability on an invalid cache or from
 * the invalidation listener!")} when the cache is queried while invalid or re-entrantly from its own
 * invalidation listener. Tom's only guards with its own {@code valid} flag, which is independent of
 * the cache's internal validity, so the call still throws &mdash; and because it runs from
 * {@code InventoryConnectorBlockEntity#updateServer} during block-entity ticking, the exception kills
 * the server thread ("Ticking block entity"). This happens when multiple connectors index the same
 * inventories (more than one connector per network) and a neighbour cache is invalidated mid-tick.
 *
 * <p>We wrap that single {@code getCapability()} call and treat the invalid-cache exception as "no
 * inventory available this tick" (return {@code null}), which is exactly what Tom's already does when
 * the cache is null/invalid. The connector simply re-indexes on a later tick. This is a safety net;
 * the underlying misconfiguration (duplicate connectors) should still be corrected.
 *
 * <p>Targeted by fully-qualified name (no compile dependency on Tom's Storage); gated on
 * {@code toms_storage} being loaded in {@link LampasMixinPlugin}.
 */
@Mixin(targets = "com.tom.storagemod.inventory.PlatformInventoryAccess$BlockInventoryAccess", remap = false)
public class TomsConnectorCacheGuardMixin {

    @WrapOperation(
            method = "get",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/capabilities/BlockCapabilityCache;getCapability()Ljava/lang/Object;",
                    remap = false
            )
    )
    private Object lampas$guardInvalidCache(BlockCapabilityCache<?, ?> cache, Operation<Object> original) {
        try {
            return original.call(cache);
        } catch (IllegalStateException e) {
            // Invalid / re-entrant cache access — behave as "no inventory this tick" rather than crash.
            return null;
        }
    }
}
