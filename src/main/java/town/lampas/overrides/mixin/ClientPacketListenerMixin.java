package town.lampas.overrides.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import town.lampas.overrides.SocialCache;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Warms the {@link SocialCache} the instant a player appears in the client's tab list, so the
 * first chat message from a newly-joined player already shows their YouTube/Twitch icon instead
 * of waiting for the throttled roster sweep in {@code ChatSocialDecorator}.
 *
 * <p>All {@code ADD_PLAYER} entries from a single packet are batched into one prefetch, so the
 * initial-connect packet (which adds every online player at once) issues a single request.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handlePlayerInfoUpdate", at = @At("TAIL"))
    private void lampasOverrides$warmSocialsOnJoin(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        if (!packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) {
            return;
        }
        List<UUID> ids = new ArrayList<>();
        for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.entries()) {
            UUID id = entry.profileId();
            if (id != null) {
                ids.add(id);
            }
        }
        if (!ids.isEmpty()) {
            SocialCache.prefetch(ids);
        }
    }
}
