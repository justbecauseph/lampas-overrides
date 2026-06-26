package town.lampas.overrides;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;

/**
 * Disables the Totem of Undying's death-save entirely. NeoForge fires
 * {@link LivingUseTotemEvent} from {@code LivingEntity.checkTotemDeathProtection}
 * before the totem is consumed; cancelling it means the totem neither saves the
 * holder nor gets used up — it becomes an inert item.
 */
public class TotemDisableHandler {
    @SubscribeEvent
    public void onUseTotem(LivingUseTotemEvent event) {
        event.setCanceled(true);
    }
}
