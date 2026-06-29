package town.lampas.overrides.mixin;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * Stops the vanilla noise generator from running at extreme coordinates, where
 * the aquifer's grid-index math overflows a 32-bit int and
 * {@code Aquifer.getAquiferStatus} indexes an array of length 315 with a large
 * negative value -> {@link ArrayIndexOutOfBoundsException}. That exception
 * leaves the chunk's generation future uncompleted, so the server thread parks
 * forever in {@code ServerChunkCache.managedBlock} awaiting it: a full,
 * unrecoverable server hang (RCON is dead because commands queue on that same
 * thread).
 *
 * <p>Why this is safe — and actually correct: Sable packs ship "sub-levels"
 * into the overworld at a hardcoded far band (~X/Z 20.48M). Any chunk in that
 * band that is not already on disk falls through to vanilla terrain gen and
 * crashes. The world border is 8000, so no legitimate terrain exists past
 * {@link #VOID_BEYOND}; returning void there affects nothing reachable, and
 * ship sub-levels want empty chunks anyway (Sable places ship blocks on top).
 * Existing shipyard chunks load from region files and are untouched — only
 * fresh generation in the far band is intercepted.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorMixin {

    /**
     * Beyond this |x| or |z| (in blocks) noise generation is skipped and the
     * chunk is left void. Chosen far past the 8000 world border but well below
     * where the aquifer int-overflow occurs (~tens of millions); only Sable's
     * ~20.48M shipyard band and corrupted out-of-bounds coordinates live here.
     */
    private static final int VOID_BEYOND = 1_000_000;

    private static boolean lampas$beyondGenLimit(int blockX, int blockZ) {
        return Math.abs(blockX) > VOID_BEYOND || Math.abs(blockZ) > VOID_BEYOND;
    }

    @Inject(method = "getBaseHeight", at = @At("HEAD"), cancellable = true)
    private void lampas$voidBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level,
                                       RandomState random, CallbackInfoReturnable<Integer> cir) {
        if (lampas$beyondGenLimit(x, z)) {
            cir.setReturnValue(level.getMinBuildHeight());
        }
    }

    @Inject(method = "getBaseColumn", at = @At("HEAD"), cancellable = true)
    private void lampas$voidBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random,
                                       CallbackInfoReturnable<NoiseColumn> cir) {
        if (lampas$beyondGenLimit(x, z)) {
            cir.setReturnValue(new NoiseColumn(height.getMinBuildHeight(), new BlockState[0]));
        }
    }

    @Inject(method = "fillFromNoise", at = @At("HEAD"), cancellable = true)
    private void lampas$voidFillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager,
                                          ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        ChunkPos pos = chunk.getPos();
        if (lampas$beyondGenLimit(pos.getMinBlockX(), pos.getMinBlockZ())) {
            cir.setReturnValue(CompletableFuture.completedFuture(chunk));
        }
    }
}
