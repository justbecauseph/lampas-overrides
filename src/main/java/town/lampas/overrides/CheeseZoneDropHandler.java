package town.lampas.overrides;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * "Cheese zone" mob-drop override. Any non-player mob that dies within the configured overworld
 * footprint(s) drops ONLY Lampia cheese — a guaranteed slice + wheel — and nothing else: normal
 * loot, worn armour and held items are all discarded. XP is untouched.
 *
 * <p>Implemented on {@link LivingDropsEvent} rather than a datapack {@code DeathLootTable} swap so
 * it also covers mobs that resolve their drops in Java: Mowzie's bosses (which ship entity loot
 * tables and can override {@code getLootTable()}), Illager Invasion illagers (no entity loot
 * tables of their own), etc. The event fires for every {@link LivingEntity} death regardless of
 * how the loot is generated. (A mob that fully overrides {@code die()} and spawns item entities by
 * hand would still slip past this, but that is rare.)
 *
 * <p>The footprints are full vertical columns (any Y). The two boxes from the original request
 * collapse to one once their thin Y bands are dropped: Box 2 (X 528..654, Z -32..-16) is wholly
 * contained in Box 1 (X 528..654, Z -244..199). The array is kept general so the boxes can be
 * split back out (e.g. with separate Y limits) without reworking the logic.
 */
public class CheeseZoneDropHandler {

    /** {minX, maxX, minZ, maxZ} — inclusive block bounds, overworld, full height. */
    private static final int[][] ZONES = {
            {528, 654, -244, 199},
    };

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;              // never touch player drops
        Level level = entity.level();
        if (level.isClientSide) return;
        if (level.dimension() != Level.OVERWORLD) return;  // overworld only

        BlockPos pos = entity.blockPosition();
        if (!inZone(pos.getX(), pos.getZ())) return;

        // Replace every normal drop with a guaranteed slice + wheel.
        event.getDrops().clear();
        double x = entity.getX(), y = entity.getY(), z = entity.getZ();
        event.getDrops().add(new ItemEntity(level, x, y, z,
                new ItemStack(ModItems.LAMPIA_CHEESE_SLICE.get())));
        event.getDrops().add(new ItemEntity(level, x, y, z,
                new ItemStack(ModItems.LAMPIA_CHEESE_WHEEL.get())));
    }

    private static boolean inZone(int x, int z) {
        for (int[] b : ZONES) {
            if (x >= b[0] && x <= b[1] && z >= b[2] && z <= b[3]) return true;
        }
        return false;
    }
}
