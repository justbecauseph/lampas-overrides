package town.lampas.overrides.mixin;

import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * Hides all boss bars server-wide by dropping every {@link ClientboundBossEventPacket} on its way
 * out to clients.
 *
 * <p>Boss bars &mdash; the vanilla wither and ender dragon bars, every modded {@code ServerBossEvent}
 * (Mowzie's Mobs, Naturalist, &hellip;), and any {@code /bossbar}-created bar &mdash; are all delivered
 * to players through a {@code ClientboundBossEventPacket}. Rather than hunt down each individual
 * boss source, we intercept the single outbound choke point every one of them funnels through:
 * {@code ServerCommonPacketListenerImpl#send(Packet, PacketSendListener)} (the no-listener
 * {@code send(Packet)} overload delegates here, so cancelling here covers both). When the packet is
 * a boss-event packet we cancel the send, so the client is never told a bar exists and renders
 * nothing. The mob's real health, AI, and combat are untouched &mdash; only the HUD element is
 * suppressed.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class BossBarSuppressMixin {

    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void lampas$suppressBossBars(Packet<?> packet, @Nullable PacketSendListener listener, CallbackInfo ci) {
        if (packet instanceof ClientboundBossEventPacket) {
            ci.cancel();
        }
    }
}
