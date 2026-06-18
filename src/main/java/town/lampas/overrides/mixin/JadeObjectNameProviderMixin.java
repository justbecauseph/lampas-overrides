package town.lampas.overrides.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes Jade's entity tooltip show a player's custom name (from {@code eclipsescustomname},
 * plus our own {@code [LIVE]} tag) instead of their raw Minecraft account name.
 *
 * <p>Jade runs purely client-side and builds the player title from
 * {@link net.minecraft.world.entity.player.Player#getDisplayName()}. On the client that call
 * returns the vanilla account name, because {@code eclipsescustomname} only rewrites
 * {@code getDisplayName()} on the <em>server</em> {@code ServerPlayer}. The custom name still
 * reaches the client, though: it is synced as the player's tab-list display name (the server's
 * {@code getPlayerListName()} returns the custom {@code getDisplayName()}). So we substitute the
 * tab-list name for players whenever one is available, wrapping only the {@code getDisplayName()}
 * call in Jade's player branch and leaving the rest of its logic (accessibility details, villagers,
 * non-players) untouched.
 *
 * <p>Targeted by fully-qualified name so we don't take a compile/runtime dependency on Jade; the
 * mixin is gated on Jade actually being loaded in {@code LampasMixinPlugin}.
 */
@Mixin(targets = "snownee.jade.addon.core.ObjectNameProvider", remap = false)
public class JadeObjectNameProviderMixin {

    @WrapOperation(
            method = "getEntityName",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getDisplayName()Lnet/minecraft/network/chat/Component;",
                    remap = true
            )
    )
    private static Component lampas$useTabListName(Entity entity, Operation<Component> original) {
        if (entity instanceof Player) {
            Minecraft minecraft = Minecraft.getInstance();
            ClientPacketListener connection = minecraft.getConnection();
            if (connection != null) {
                PlayerInfo info = connection.getPlayerInfo(entity.getUUID());
                if (info != null) {
                    Component tabListName = info.getTabListDisplayName();
                    if (tabListName != null) {
                        return tabListName;
                    }
                }
            }
        }
        return original.call(entity);
    }
}
