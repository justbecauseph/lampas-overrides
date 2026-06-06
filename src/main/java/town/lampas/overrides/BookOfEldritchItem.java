package town.lampas.overrides;

import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class BookOfEldritchItem extends Item {
    public BookOfEldritchItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.consume(itemstack);
        }

        // Check faction
        Faction faction = PlayerActivityListener.getPlayerFaction(player.getUUID());
        if (faction != Faction.RELIGION) {
            player.sendSystemMessage(Component.literal("Only members of the RELIGION faction can use this book.").withStyle(ChatFormatting.RED));
            return InteractionResultHolder.fail(itemstack);
        }

        // Apply totem of undying buff for 1 hour to 10x10 area of effect to players
        // Bounding box: inflate by 5 blocks in all directions around the player
        AABB aabb = player.getBoundingBox().inflate(5.0D);
        List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class, aabb);

        // 1 hour = 3600 seconds = 72000 ticks
        int durationTicks = 72000;
        MobEffectInstance effectInstance = new MobEffectInstance(ModEffects.TOTEM_EFFECT, durationTicks, 0, false, false, true);

        for (Player target : nearbyPlayers) {
            target.addEffect(new MobEffectInstance(effectInstance));
            target.sendSystemMessage(Component.literal("The Book of Eldritch has blessed you with the Totem of Undying protection for 1 hour.").withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        // Consume the item
        if (!player.isCreative()) {
            itemstack.shrink(1);
        }

        return InteractionResultHolder.consume(itemstack);
    }
}
