package town.lampas.overrides;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

public class CheesyContactHandler {
    private static final int CONTACT_DURATION_TICKS = 72000; // 1 hour

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player carrier = event.getEntity();
        if (carrier.level().isClientSide) return;
        if (carrier.tickCount % 20 != 0) return; // throttle to ~1x/sec
        if (!carrier.hasEffect(CarrierStatus.getCarrierEffect())) return;

        AABB contactBox = new AABB(
                carrier.getX() - 1.0, carrier.getY() - 2.0, carrier.getZ() - 1.0,
                carrier.getX() + 1.0, carrier.getY() + 3.0, carrier.getZ() + 1.0);

        List<Player> nearby = carrier.level().getEntitiesOfClass(Player.class, contactBox);
        for (Player other : nearby) {
            if (other.getUUID().equals(carrier.getUUID())) continue;
            other.addEffect(new MobEffectInstance(CarrierStatus.getContactEffect(), CONTACT_DURATION_TICKS, 0, false, false, true));
        }
    }
}
