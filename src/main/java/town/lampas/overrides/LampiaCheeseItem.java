package town.lampas.overrides;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Conrad &amp; Crowley's "Lampia cheese" — a hyper-processed cheese <em>product</em> that tastes
 * great and fills you up (its {@link net.minecraft.world.food.FoodProperties} restore hunger and
 * saturation with no immediate downside), but is engineered to be addictive. Each bite deepens the
 * eater's addiction via {@link LampiaAddiction}; the harm comes later, as withdrawal, if they stop.
 *
 * <p>{@code potency} is how much one serving advances the addiction (a wheel hooks you harder than
 * a slice).
 */
public class LampiaCheeseItem extends Item {
    private final int potency;

    public LampiaCheeseItem(Item.Properties properties, int potency) {
        super(properties);
        this.potency = potency;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        // Vanilla applies the FoodProperties (hunger/saturation) inside super; we layer the
        // addiction on top, server-side only.
        if (!level.isClientSide && livingEntity instanceof ServerPlayer player) {
            LampiaAddiction.onConsume(player, potency);
        }
        return super.finishUsingItem(stack, level, livingEntity);
    }
}
