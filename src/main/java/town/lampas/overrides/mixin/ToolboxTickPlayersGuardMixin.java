package town.lampas.overrides.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Stops Create's toolbox from killing the server thread with a {@link ConcurrentModificationException}
 * while ticking.
 *
 * <p>{@code ToolboxBlockEntity} stores its connected players as
 * {@code Map<Integer, WeakHashMap<Player, Integer>>} ({@code connectedPlayers}), and
 * {@code tickPlayers()} iterates each per-slot {@link WeakHashMap} on the server thread. {@link WeakHashMap}
 * is not thread-safe. Nothing inside the loop structurally modifies that inner map (the {@code put}/{@code
 * remove} in {@code ToolboxHandler} act on the unrelated {@code toolboxes} map), so the observed
 * {@code ConcurrentModificationException} in {@code WeakHashMap$HashIterator.nextEntry}
 * (ToolboxBlockEntity.java:137) can only come from another thread mutating {@code connectedPlayers} while
 * the tick iterates it &mdash; plausible in this pack, where c2me / createthreadedtrains / sable touch
 * players and block entities off the main thread. Because it happens during block-entity ticking, the
 * exception propagates out as "Ticking block entity" and terminates the server thread, leaving a zombie
 * JVM (game stops ticking; only non-daemon threads keep the process alive).
 *
 * <p>We wrap the single {@code WeakHashMap.entrySet()} call inside {@code tickPlayers} (the inner one; the
 * outer iteration is over a plain {@code Map}) and iterate a defensive snapshot instead of the live weak
 * map. The snapshot is immune to concurrent structural modification, and if the copy itself races with a
 * concurrent mutation we fall back to an empty set &mdash; skipping at most one tick of toolbox equip
 * sync for that slot, which is cosmetically irrelevant and self-corrects next tick. The loop never removes
 * via this inner iterator, so snapshotting is behaviourally safe.
 *
 * <p>Targeted by fully-qualified name (no compile dependency on Create); gated on {@code create} being
 * loaded in {@link LampasMixinPlugin}.
 */
@Mixin(targets = "com.simibubi.create.content.equipment.toolbox.ToolboxBlockEntity", remap = false)
public class ToolboxTickPlayersGuardMixin {

    @WrapOperation(
            method = "tickPlayers",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/WeakHashMap;entrySet()Ljava/util/Set;",
                    remap = false
            )
    )
    private Set<?> lampas$snapshotConnectedPlayers(WeakHashMap<?, ?> connected, Operation<Set<?>> original) {
        try {
            return new HashMap<>(connected).entrySet();
        } catch (ConcurrentModificationException e) {
            // Another thread mutated the weak map during the copy; skip this slot's players this tick.
            return Collections.emptySet();
        }
    }
}
