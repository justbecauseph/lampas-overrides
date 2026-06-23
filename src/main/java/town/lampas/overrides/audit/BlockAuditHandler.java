package town.lampas.overrides.audit;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import town.lampas.overrides.ModConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records direct player block placements/destructions into the local {@link BlockAuditStore}
 * and exposes the {@code /blockaudit} lookup/rollback/restore commands.
 *
 * <p>The block event handlers run at {@link EventPriority#LOWEST} so that any protection
 * mod that cancels the event runs first — we only log changes that actually happen.
 */
public class BlockAuditHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Hard cap on rows applied by a single rollback/restore, to bound the tick cost. */
    private static final int MAX_APPLY = 50_000;
    private static final int LOOKUP_LIMIT = 50;
    /** How often (in ticks) each player's inventory is scanned for watched/contraband items. */
    private static final int SCAN_INTERVAL_TICKS = 40;

    /** Watched item ids each online player is currently holding, so we only log new acquisitions. */
    private static final Map<UUID, Set<String>> WATCHED_HELD = new ConcurrentHashMap<>();

    /** Per-dimension id string, cached to avoid allocating a String on every block event. */
    private static final Map<ResourceKey<Level>, String> DIM_CACHE = new ConcurrentHashMap<>();

    /** Config lists pre-hashed into sets on (re)load for O(1) membership checks on the hot path. */
    private static volatile Set<String> ignoredBlocks = Set.of();
    private static volatile Set<String> watchedItems = Set.of();

    /** Refreshes the cached lookup sets whenever this mod's config is loaded or reloaded. */
    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == ModConfig.SPEC) {
            ignoredBlocks = Set.copyOf(ModConfig.BLOCK_AUDIT_IGNORED_BLOCKS.get());
            watchedItems = Set.copyOf(ModConfig.BLOCK_AUDIT_WATCHED_ITEMS.get());
        }
    }

    private static String dimId(Level level) {
        return DIM_CACHE.computeIfAbsent(level.dimension(), key -> key.location().toString());
    }

    // --- server lifecycle --------------------------------------------------

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        if (ModConfig.BLOCK_AUDIT_ENABLED.get()) {
            BlockAuditStore.init(event.getServer());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        BlockAuditStore.shutdown();
    }

    // --- block events ------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !ModConfig.BLOCK_AUDIT_ENABLED.get()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || !(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        BlockState old = event.getState();
        if (isIgnored(old)) {
            return;
        }
        record(player, level, event.getPos(), BlockAuditRecord.Action.BREAK, old, Blocks.AIR.defaultBlockState());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        // Multi-block placements (beds/doors) are posted as EntityMultiPlaceEvent and
        // handled below; guard against any double-dispatch.
        if (event instanceof BlockEvent.EntityMultiPlaceEvent) {
            return;
        }
        if (event.isCanceled() || !ModConfig.BLOCK_AUDIT_ENABLED.get()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)
                || !(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        BlockState placed = event.getPlacedBlock();
        if (isIgnored(placed)) {
            return;
        }
        record(player, level, event.getPos(), BlockAuditRecord.Action.PLACE,
                event.getBlockSnapshot().getState(), placed);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (event.isCanceled() || !ModConfig.BLOCK_AUDIT_ENABLED.get()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)
                || !(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        BlockAuditStore store = BlockAuditStore.get();
        if (store == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String dim = dimId(level);
        String name = player.getGameProfile().getName();
        UUID uuid = player.getUUID();
        for (net.neoforged.neoforge.common.util.BlockSnapshot snap : event.getReplacedBlockSnapshots()) {
            BlockState placed = snap.getCurrentState();
            if (isIgnored(placed)) {
                continue;
            }
            BlockPos p = snap.getPos();
            store.enqueue(new BlockAuditRecord(now, uuid, name, BlockAuditRecord.Action.PLACE,
                    dim, p.getX(), p.getY(), p.getZ(), snap.getState(), placed));
        }
    }

    // --- contraband / watched-item scan -----------------------------------

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!ModConfig.BLOCK_AUDIT_ENABLED.get()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Throttle: scan each player only every SCAN_INTERVAL_TICKS ticks. Creative/spectator
        // players legitimately handle these items, so they're exempt.
        if (player.tickCount % SCAN_INTERVAL_TICKS != 0 || player.isCreative() || player.isSpectator()) {
            return;
        }
        Set<String> watchlist = watchedItems;
        if (watchlist.isEmpty()) {
            WATCHED_HELD.remove(player.getUUID());
            return;
        }
        Set<String> held = collectWatched(player, watchlist);
        Set<String> previous = WATCHED_HELD.getOrDefault(player.getUUID(), Set.of());
        for (String itemId : held) {
            if (!previous.contains(itemId)) {
                flagObtained(player, itemId);
            }
        }
        if (held.isEmpty()) {
            WATCHED_HELD.remove(player.getUUID());
        } else {
            WATCHED_HELD.put(player.getUUID(), held);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        WATCHED_HELD.remove(event.getEntity().getUUID());
    }

    private static Set<String> collectWatched(ServerPlayer player, Set<String> watchlist) {
        Set<String> found = new HashSet<>();
        scanContainer(player.getInventory(), watchlist, found);
        scanContainer(player.getEnderChestInventory(), watchlist, found);
        return found;
    }

    private static void scanContainer(Container container, Set<String> watchlist, Set<String> found) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null && watchlist.contains(id.toString())) {
                found.add(id.toString());
            }
        }
    }

    private static void flagObtained(ServerPlayer player, String itemId) {
        BlockPos pos = player.blockPosition();
        String dim = dimId(player.level());
        String name = player.getGameProfile().getName();
        LOGGER.warn("CONTRABAND: {} ({}) is holding {} in {} at [{}, {}, {}]",
                name, player.getUUID(), itemId, dim, pos.getX(), pos.getY(), pos.getZ());
        BlockAuditStore store = BlockAuditStore.get();
        if (store == null) {
            return;
        }
        // Record the item's block form (if any) so /blockaudit lookup can show its id.
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        Block block = Block.byItem(item);
        BlockState newState = block == Blocks.AIR ? null : block.defaultBlockState();
        store.enqueue(new BlockAuditRecord(System.currentTimeMillis(), player.getUUID(),
                name, BlockAuditRecord.Action.OBTAIN, dim,
                pos.getX(), pos.getY(), pos.getZ(), null, newState));
    }

    private static void record(Player player, Level level, BlockPos pos, BlockAuditRecord.Action action,
                               BlockState oldState, BlockState newState) {
        BlockAuditStore store = BlockAuditStore.get();
        if (store == null) {
            return;
        }
        store.enqueue(new BlockAuditRecord(
                System.currentTimeMillis(),
                player.getUUID(),
                player.getGameProfile().getName(),
                action,
                dimId(level),
                pos.getX(), pos.getY(), pos.getZ(),
                oldState, newState));
    }

    private static boolean isIgnored(BlockState state) {
        Set<String> ignored = ignoredBlocks;
        if (ignored.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && ignored.contains(id.toString());
    }

    // --- commands ----------------------------------------------------------

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("blockaudit")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("lookup")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 256))
                                        .executes(ctx -> lookup(ctx, radius(ctx), 0, null))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(0))
                                                .executes(ctx -> lookup(ctx, radius(ctx), minutes(ctx), null))
                                                .then(Commands.argument("player", StringArgumentType.word())
                                                        .executes(ctx -> lookup(ctx, radius(ctx), minutes(ctx), playerArg(ctx)))))))
                        .then(Commands.literal("rollback")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 256))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                                .executes(ctx -> apply(ctx, radius(ctx), minutes(ctx), null, false))
                                                .then(Commands.argument("player", StringArgumentType.word())
                                                        .executes(ctx -> apply(ctx, radius(ctx), minutes(ctx), playerArg(ctx), false))))))
                        .then(Commands.literal("restore")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 256))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                                .executes(ctx -> apply(ctx, radius(ctx), minutes(ctx), null, true))
                                                .then(Commands.argument("player", StringArgumentType.word())
                                                        .executes(ctx -> apply(ctx, radius(ctx), minutes(ctx), playerArg(ctx), true)))))));
    }

    private static int radius(CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, "radius");
    }

    private static int minutes(CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, "minutes");
    }

    private static String playerArg(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "player");
    }

    private int lookup(CommandContext<CommandSourceStack> ctx, int radius, int minutes, String playerName) {
        CommandSourceStack source = ctx.getSource();
        BlockAuditStore store = BlockAuditStore.get();
        if (store == null) {
            source.sendFailure(Component.literal("Block audit is not active."));
            return 0;
        }
        UUID uuid = resolvePlayer(source, playerName);
        if (playerName != null && uuid == null) {
            source.sendFailure(Component.literal("Unknown player: " + playerName));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos center = BlockPos.containing(source.getPosition());
        long since = minutes <= 0 ? 0L : System.currentTimeMillis() - (long) minutes * 60_000L;
        List<BlockAuditStore.LogEntry> rows = store.lookup(level.dimension().location().toString(),
                center.getX(), center.getZ(), radius, since, uuid, LOOKUP_LIMIT);
        if (rows.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No block audit entries found."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("=== Block Audit (" + rows.size() + ") ==="), false);
        long now = System.currentTimeMillis();
        for (BlockAuditStore.LogEntry e : rows) {
            String verb;
            String block;
            switch (e.action()) {
                case BREAK -> {
                    verb = "broke";
                    block = blockId(e.oldStateSnbt());
                }
                case OBTAIN -> {
                    verb = "obtained";
                    block = blockId(e.newStateSnbt());
                }
                default -> {
                    verb = "placed";
                    block = blockId(e.newStateSnbt());
                }
            }
            String line = String.format("%s ago  %s  %s  %s  @ %d,%d,%d",
                    formatAgo(now - e.time()),
                    e.playerName() == null ? "?" : e.playerName(),
                    verb, block, e.x(), e.y(), e.z());
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return rows.size();
    }

    private int apply(CommandContext<CommandSourceStack> ctx, int radius, int minutes, String playerName, boolean restore) {
        CommandSourceStack source = ctx.getSource();
        BlockAuditStore store = BlockAuditStore.get();
        if (store == null) {
            source.sendFailure(Component.literal("Block audit is not active."));
            return 0;
        }
        UUID uuid = resolvePlayer(source, playerName);
        if (playerName != null && uuid == null) {
            source.sendFailure(Component.literal("Unknown player: " + playerName));
            return 0;
        }
        ServerLevel level = source.getLevel();
        String dim = level.dimension().location().toString();
        BlockPos center = BlockPos.containing(source.getPosition());
        long since = System.currentTimeMillis() - (long) minutes * 60_000L;
        List<BlockAuditStore.StateChange> changes = restore
                ? store.selectForRestore(dim, center.getX(), center.getZ(), radius, since, uuid, MAX_APPLY)
                : store.selectForRollback(dim, center.getX(), center.getZ(), radius, since, uuid, MAX_APPLY);

        HolderGetter<Block> blocks = source.registryAccess().lookupOrThrow(Registries.BLOCK);
        List<Long> applied = new ArrayList<>(changes.size());
        int failed = 0;
        for (BlockAuditStore.StateChange c : changes) {
            BlockState state = parseState(blocks, c.stateSnbt());
            if (state == null) {
                failed++;
                continue;
            }
            level.setBlock(new BlockPos(c.x(), c.y(), c.z()), state, Block.UPDATE_ALL);
            applied.add(c.id());
        }
        store.markRolledBack(applied, !restore);

        int failedCount = failed;
        source.sendSuccess(() -> Component.literal(
                (restore ? "Restored " : "Rolled back ") + applied.size() + " change(s)"
                        + (failedCount > 0 ? " (" + failedCount + " unreadable, skipped)" : "")
                        + (applied.size() == MAX_APPLY ? " [hit " + MAX_APPLY + " cap; run again to continue]" : "")
                        + "."), true);
        return applied.size();
    }

    private static UUID resolvePlayer(CommandSourceStack source, String name) {
        if (name == null) {
            return null;
        }
        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getUUID();
        }
        var cache = source.getServer().getProfileCache();
        return cache == null ? null : cache.get(name).map(GameProfile::getId).orElse(null);
    }

    private static BlockState parseState(HolderGetter<Block> blocks, String snbt) {
        if (snbt == null) {
            return null;
        }
        try {
            CompoundTag tag = TagParser.parseTag(snbt);
            return NbtUtils.readBlockState(blocks, tag);
        } catch (Exception e) {
            return null;
        }
    }

    /** Extracts the block id ("Name") directly from a serialized block-state SNBT. */
    private static String blockId(String snbt) {
        if (snbt == null) {
            return "?";
        }
        try {
            return TagParser.parseTag(snbt).getString("Name");
        } catch (Exception e) {
            return "?";
        }
    }

    private static String formatAgo(long millis) {
        long seconds = Math.max(0, millis / 1000L);
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h";
        }
        return (hours / 24) + "d";
    }
}
