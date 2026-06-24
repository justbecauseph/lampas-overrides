package town.lampas.overrides;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LampasOverridesMod.MODID);

    // Pale, factory-pressed block of Lampia cheese — squishy/sticky like the real thing it imitates.
    public static final DeferredBlock<Block> LAMPIA_CHEESE_BLOCK = BLOCKS.registerSimpleBlock("lampia_cheese_block",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND)
                    .strength(0.5F)
                    .sound(SoundType.HONEY_BLOCK));
}
