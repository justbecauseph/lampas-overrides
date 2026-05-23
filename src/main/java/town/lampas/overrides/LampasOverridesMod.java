package town.lampas.overrides;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;
import java.util.Optional;

@Mod(LampasOverridesMod.MODID)
@EventBusSubscriber(modid = LampasOverridesMod.MODID)
public class LampasOverridesMod {
    public static final String MODID = "lampas_overrides";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation COIN_EMERALD_ID = ResourceLocation.fromNamespaceAndPath("lightmanscurrency", "coin_emerald");

    public LampasOverridesMod() {
        LOGGER.info("Lampas Trade Overrides mod initialized!");
    }

    private static Item getCoinItem() {
        return BuiltInRegistries.ITEM.get(COIN_EMERALD_ID);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onVillagerTrades(VillagerTradesEvent event) {
        LOGGER.info("Replacing emeralds in villager trades for: {}", event.getType());
        event.getTrades().forEach((level, list) -> {
            replaceTrades(list);
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onWandererTrades(WandererTradesEvent event) {
        LOGGER.info("Replacing emeralds in wandering trader trades.");
        replaceTrades(event.getGenericTrades());
        replaceTrades(event.getRareTrades());
    }

    private static void replaceTrades(List<VillagerTrades.ItemListing> trades) {
        trades.replaceAll(trade -> {
            if (trade instanceof WrappingItemListing) {
                return trade;
            }
            return new WrappingItemListing(trade);
        });
    }

    private static class WrappingItemListing implements VillagerTrades.ItemListing {
        private final VillagerTrades.ItemListing original;

        public WrappingItemListing(VillagerTrades.ItemListing original) {
            this.original = original;
        }

        @Override
        public MerchantOffer getOffer(Entity entity, RandomSource randomSource) {
            MerchantOffer offer = original.getOffer(entity, randomSource);
            if (offer == null) {
                return null;
            }

            Item coin = getCoinItem();
            if (coin == Items.AIR || coin == null) {
                return offer;
            }

            ItemCost costA = offer.getItemCostA();
            boolean changed = false;
            if (costA.item().value() == Items.EMERALD) {
                costA = new ItemCost(BuiltInRegistries.ITEM.wrapAsHolder(coin), costA.count(), costA.components());
                changed = true;
            }

            Optional<ItemCost> costB = offer.getItemCostB();
            if (costB.isPresent()) {
                ItemCost costBVal = costB.get();
                if (costBVal.item().value() == Items.EMERALD) {
                    costB = Optional.of(new ItemCost(BuiltInRegistries.ITEM.wrapAsHolder(coin), costBVal.count(), costBVal.components()));
                    changed = true;
                }
            }

            ItemStack result = offer.getResult();
            if (result.getItem() == Items.EMERALD) {
                result = result.transmuteCopy(coin, result.getCount());
                changed = true;
            }

            if (!changed) {
                return offer;
            }

            return new MerchantOffer(
                costA,
                costB,
                result,
                offer.getUses(),
                offer.getMaxUses(),
                offer.getXp(),
                offer.getPriceMultiplier(),
                offer.getDemand()
            );
        }
    }
}
