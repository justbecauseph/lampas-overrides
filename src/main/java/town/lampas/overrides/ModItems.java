package town.lampas.overrides;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LampasOverridesMod.MODID);

    public static final DeferredItem<Item> BOOK_OF_ELDRITCH = ITEMS.register("book_of_eldritch",
            () -> new BookOfEldritchItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final DeferredItem<Item> ELDRITCH_STONE = ITEMS.register("eldritch_stone",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    // Lampia cheese — tastes great and fills you up, no immediate downside (the catch is addiction).
    private static final FoodProperties LAMPIA_SLICE_FOOD = new FoodProperties.Builder()
            .nutrition(4).saturationModifier(0.4F).build();
    private static final FoodProperties LAMPIA_WHEEL_FOOD = new FoodProperties.Builder()
            .nutrition(8).saturationModifier(0.8F).build();

    public static final DeferredItem<Item> LAMPIA_CHEESE_SLICE = ITEMS.register("lampia_cheese_slice",
            () -> new LampiaCheeseItem(new Item.Properties().food(LAMPIA_SLICE_FOOD), 1));

    public static final DeferredItem<Item> LAMPIA_CHEESE_WHEEL = ITEMS.register("lampia_cheese_wheel",
            () -> new LampiaCheeseItem(new Item.Properties().stacksTo(16).food(LAMPIA_WHEEL_FOOD), 3));

    public static final DeferredItem<BlockItem> LAMPIA_CHEESE_BLOCK =
            ITEMS.registerSimpleBlockItem("lampia_cheese_block", ModBlocks.LAMPIA_CHEESE_BLOCK);

    // Lampia antidote — drink it to purge the cheese addiction outright (see LampiaCureItem).
    public static final DeferredItem<Item> LAMPIA_ANTIDOTE = ITEMS.register("lampia_antidote",
            () -> new LampiaCureItem(new Item.Properties().stacksTo(16)));
}
