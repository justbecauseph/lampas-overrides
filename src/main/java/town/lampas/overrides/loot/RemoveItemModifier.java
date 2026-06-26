package town.lampas.overrides.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Generic global loot modifier that strips a single item out of every loot roll.
 * Used to keep {@code minecraft:totem_of_undying} from ever being generated as
 * loot (evoker/raid drops, chest & structure loot, modded tables). Because GLMs
 * run on every loot-table query, this catches Lootr's per-player chests too.
 *
 * <p>Only affects loot <em>generation</em> — totems already in inventories,
 * ender chests or block storage are untouched.
 */
public class RemoveItemModifier extends LootModifier {
    public static final MapCodec<RemoveItemModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
            LootModifier.codecStart(inst)
                    .and(BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item").forGetter(m -> m.item))
                    .apply(inst, RemoveItemModifier::new));

    private final Holder<Item> item;

    public RemoveItemModifier(LootItemCondition[] conditions, Holder<Item> item) {
        super(conditions);
        this.item = item;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        generatedLoot.removeIf(stack -> stack.is(this.item));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return ModLootModifiers.REMOVE_ITEM.get();
    }
}
