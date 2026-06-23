package town.lampas.overrides.audit;

import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * One captured block change, queued from the server thread for off-thread persistence.
 *
 * <p>Only immutable values are stored. {@link BlockState} instances are interned and
 * immutable, so handing the references to the flusher thread is safe and avoids any
 * NBT serialization on the game thread.
 */
public record BlockAuditRecord(
        long time,            // epoch millis
        UUID player,
        String playerName,
        Action action,
        String dimension,     // e.g. "minecraft:overworld"
        int x,
        int y,
        int z,
        BlockState oldState,  // state before the change (broken/replaced block)
        BlockState newState   // state after the change (air for break, placed block for place)
) {
    public enum Action {
        BREAK(0),
        PLACE(1),
        /** A player was found holding a watched/contraband item (e.g. bedrock). Position is the player's location. */
        OBTAIN(2);

        private final int id;

        Action(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static Action fromId(int id) {
            for (Action a : values()) {
                if (a.id == id) {
                    return a;
                }
            }
            return BREAK;
        }
    }
}
