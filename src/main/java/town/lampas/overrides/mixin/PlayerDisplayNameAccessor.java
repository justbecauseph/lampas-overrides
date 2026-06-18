package town.lampas.overrides.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes NeoForge's private cached {@code displayname} field on {@link Player} so it can be
 * invalidated (set to {@code null}) without recomputing it.
 *
 * <p>{@link net.minecraft.server.level.ServerPlayer#refreshDisplayName()} would <em>recompute</em>
 * the cache by firing {@code PlayerEvent.NameFormat} with the raw {@code getName()} value. That
 * bypasses mods like {@code eclipsescustomname}, which apply their prefix/nickname/suffix by
 * wrapping the {@code getName()} call <em>inside</em> {@code getDisplayName()} only. Nulling the
 * field instead forces a lazy recompute through {@code getDisplayName()}, where those mods
 * participate again. See {@code LiveStatusHandler#refreshNames}.
 */
@Mixin(Player.class)
public interface PlayerDisplayNameAccessor {
    @Accessor("displayname")
    void lampas$setDisplayName(Component displayname);
}
