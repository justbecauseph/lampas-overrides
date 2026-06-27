package town.lampas.overrides;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * The antidote to Conrad &amp; Crowley's "Lampia cheese". Drinking it purges the addiction outright:
 * the hidden level resets to zero and any active {@link LampiaWithdrawalEffect} is lifted (see
 * {@link LampiaAddiction#cure}). The mirror image of {@link LampiaCheeseItem} — where the cheese
 * deepens the hook, the tonic breaks it.
 *
 * <p>It's a drink, not a food: it restores no hunger/saturation, so it can be downed at any time and
 * exists purely to clear the addiction.
 */
public class LampiaCureItem extends Item {

    public LampiaCureItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        if (!level.isClientSide && livingEntity instanceof ServerPlayer player) {
            LampiaAddiction.cure(player);
        }
        // No FoodProperties to apply, so handle the stack shrink ourselves (mirrors vanilla drinks).
        if (livingEntity instanceof ServerPlayer player && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }
}
