package town.lampas.overrides;

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
}
