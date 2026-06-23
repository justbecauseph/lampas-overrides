package town.lampas.overrides.audit;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import town.lampas.overrides.ModConfig;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the bounded in-memory queue, a single background flusher thread, and the SQLite
 * write connection for the block audit log.
 *
 * <p>Performance contract: {@link #enqueue} is the only method called from the server
 * thread and does nothing but a non-blocking {@link ArrayBlockingQueue#offer}. All NBT
 * serialization and disk I/O happen on the dedicated flusher thread, which owns the
 * write connection exclusively (sidestepping SQLite's single-thread-per-connection rule).
 * Lookups/rollbacks open their own short-lived read connections; WAL mode lets readers
 * run concurrently with the writer.
 */
public final class BlockAuditStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile BlockAuditStore INSTANCE;
    private static volatile Driver sqliteDriver;

    private final Path dbPath;
    private final ArrayBlockingQueue<BlockAuditRecord> queue;
    private final int batchSize;
    private final long flushIntervalMs;
    private final Thread flusher;
    private volatile boolean running = true;
    private final AtomicLong dropped = new AtomicLong();
    private volatile long lastDropLog = 0;

    private BlockAuditStore(Path dbPath, int maxQueue, int batchSize, long flushIntervalMs) {
        this.dbPath = dbPath;
        this.queue = new ArrayBlockingQueue<>(maxQueue);
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        this.flusher = new Thread(this::runFlusher, "lampas-block-audit");
        this.flusher.setDaemon(true);
    }

    // --- lifecycle ---------------------------------------------------------

    public static void init(MinecraftServer server) {
        if (INSTANCE != null) {
            return;
        }
        Path dbPath = server.getWorldPath(LevelResource.ROOT).resolve("lampas_audit.db");
        BlockAuditStore store = new BlockAuditStore(
                dbPath,
                ModConfig.BLOCK_AUDIT_MAX_QUEUE.get(),
                ModConfig.BLOCK_AUDIT_BATCH_SIZE.get(),
                ModConfig.BLOCK_AUDIT_FLUSH_INTERVAL_MS.get());
        INSTANCE = store;
        store.flusher.start();
        LOGGER.info("Block audit log opened at {}", dbPath);
    }

    public static BlockAuditStore get() {
        return INSTANCE;
    }

    public static void shutdown() {
        BlockAuditStore store = INSTANCE;
        INSTANCE = null;
        if (store != null) {
            store.stop();
        }
    }

    private void stop() {
        running = false;
        flusher.interrupt();
        try {
            flusher.join(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long d = dropped.get();
        if (d > 0) {
            LOGGER.warn("Block audit dropped {} events total this session (queue saturation).", d);
        }
    }

    // --- producer side (server thread) -------------------------------------

    public void enqueue(BlockAuditRecord record) {
        if (!queue.offer(record)) {
            long n = dropped.incrementAndGet();
            long now = System.currentTimeMillis();
            if (now - lastDropLog > 10000) {
                lastDropLog = now;
                LOGGER.warn("Block audit queue full; dropped {} events so far. Raise blockAuditMaxQueue or lower blockAuditFlushIntervalMs.", n);
            }
        }
    }

    // --- consumer side (flusher thread) ------------------------------------

    private void runFlusher() {
        Connection conn = null;
        try {
            conn = connect("jdbc:sqlite:" + dbPath);
            // PRAGMAs that change the journal mode must run outside a transaction,
            // so apply them while still in autocommit mode.
            applyPragmas(conn);
            conn.setAutoCommit(false);
            initSchema(conn);
            pruneRetention(conn);
            conn.commit();

            List<BlockAuditRecord> batch = new ArrayList<>(batchSize);
            while (running || !queue.isEmpty()) {
                BlockAuditRecord first;
                try {
                    first = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // Shutdown signal; loop re-checks `running` and drains the queue.
                    continue;
                }
                if (first == null) {
                    continue;
                }
                batch.clear();
                batch.add(first);
                queue.drainTo(batch, batchSize - 1);
                writeBatch(conn, batch);
            }
        } catch (Exception e) {
            LOGGER.error("Block audit flusher crashed; audit logging stopped for this session: {}", e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    LOGGER.error("Failed to close block audit DB: {}", e.getMessage(), e);
                }
            }
        }
    }

    private void applyPragmas(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
        }
    }

    private void initSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS block_log (
                      id           INTEGER PRIMARY KEY AUTOINCREMENT,
                      time         INTEGER NOT NULL,
                      player       TEXT    NOT NULL,
                      player_name  TEXT,
                      action       INTEGER NOT NULL,
                      dim          TEXT    NOT NULL,
                      x            INTEGER NOT NULL,
                      y            INTEGER NOT NULL,
                      z            INTEGER NOT NULL,
                      old_state    TEXT,
                      new_state    TEXT,
                      rolled_back  INTEGER NOT NULL DEFAULT 0
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_block_log_pos ON block_log(dim, x, z, time)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_block_log_player ON block_log(player, time)");
        }
    }

    private void pruneRetention(Connection conn) throws SQLException {
        int days = ModConfig.BLOCK_AUDIT_RETENTION_DAYS.get();
        if (days <= 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - (long) days * 86_400_000L;
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM block_log WHERE time < ?")) {
            ps.setLong(1, cutoff);
            int removed = ps.executeUpdate();
            if (removed > 0) {
                LOGGER.info("Block audit retention pruned {} rows older than {} days.", removed, days);
            }
        }
    }

    private void writeBatch(Connection conn, List<BlockAuditRecord> batch) {
        String sql = "INSERT INTO block_log(time,player,player_name,action,dim,x,y,z,old_state,new_state) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (BlockAuditRecord r : batch) {
                ps.setLong(1, r.time());
                ps.setString(2, r.player().toString());
                ps.setString(3, r.playerName());
                ps.setInt(4, r.action().id());
                ps.setString(5, r.dimension());
                ps.setInt(6, r.x());
                ps.setInt(7, r.y());
                ps.setInt(8, r.z());
                ps.setString(9, serializeState(r.oldState()));
                ps.setString(10, serializeState(r.newState()));
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            LOGGER.error("Failed to write block audit batch ({} rows): {}", batch.size(), e.getMessage(), e);
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // already logged the primary failure
            }
        }
    }

    /** SNBT representation of a block state; null-safe. */
    private static String serializeState(net.minecraft.world.level.block.state.BlockState state) {
        if (state == null) {
            return null;
        }
        return NbtUtils.writeBlockState(state).toString();
    }

    // --- reader side (command thread) --------------------------------------

    /** A single audit row for display in {@code /blockaudit lookup}. */
    public record LogEntry(long time, String playerName, BlockAuditRecord.Action action,
                           int x, int y, int z, String oldStateSnbt, String newStateSnbt) {}

    /** A change selected for rollback/restore: the row id, position, and target state SNBT. */
    public record StateChange(long id, int x, int y, int z, String stateSnbt) {}

    private Connection openRead() throws SQLException {
        // Note: sqlite-jdbc rejects Connection#setReadOnly after connecting (it only honors
        // read-only via SQLiteConfig before opening). We don't set it: these connections only
        // run SELECTs, and under WAL a normal connection reads concurrently with the writer.
        return connect("jdbc:sqlite:" + dbPath);
    }

    /**
     * Opens a SQLite connection without going through {@link java.sql.DriverManager}, which
     * filters drivers by the caller's classloader visibility. The bundled sqlite-jdbc lives on
     * the mod's module layer in production but on the system classpath in a dev runtime, so we
     * resolve the driver class against whichever loader can see it and connect through it directly.
     */
    private static Connection connect(String url) throws SQLException {
        Connection conn = driver().connect(url, new Properties());
        if (conn == null) {
            throw new SQLException("SQLite driver returned no connection for " + url);
        }
        return conn;
    }

    private static Driver driver() throws SQLException {
        Driver d = sqliteDriver;
        if (d != null) {
            return d;
        }
        synchronized (BlockAuditStore.class) {
            if (sqliteDriver != null) {
                return sqliteDriver;
            }
            ClassLoader[] loaders = {
                    BlockAuditStore.class.getClassLoader(),
                    Thread.currentThread().getContextClassLoader(),
                    ClassLoader.getSystemClassLoader()
            };
            ClassNotFoundException notFound = null;
            for (ClassLoader cl : loaders) {
                if (cl == null) {
                    continue;
                }
                try {
                    Class<?> c = Class.forName("org.sqlite.JDBC", true, cl);
                    sqliteDriver = (Driver) c.getDeclaredConstructor().newInstance();
                    return sqliteDriver;
                } catch (ClassNotFoundException e) {
                    notFound = e;
                } catch (ReflectiveOperationException e) {
                    throw new SQLException("Failed to instantiate SQLite JDBC driver", e);
                }
            }
            throw new SQLException("SQLite JDBC driver (org.sqlite.JDBC) not found on any classloader", notFound);
        }
    }

    public List<LogEntry> lookup(String dim, int cx, int cz, int radius, long sinceMillis,
                                 UUID playerFilter, int limit) {
        String sql = "SELECT time,player_name,action,x,y,z,old_state,new_state FROM block_log "
                + "WHERE dim=? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ? AND time>=?"
                + (playerFilter != null ? " AND player=?" : "")
                + " ORDER BY time DESC LIMIT ?";
        List<LogEntry> out = new ArrayList<>();
        try (Connection conn = openRead(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = bindArea(ps, dim, cx, cz, radius, sinceMillis, playerFilter);
            ps.setInt(i, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new LogEntry(
                            rs.getLong("time"),
                            rs.getString("player_name"),
                            BlockAuditRecord.Action.fromId(rs.getInt("action")),
                            rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                            rs.getString("old_state"), rs.getString("new_state")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Block audit lookup failed: {}", e.getMessage(), e);
        }
        return out;
    }

    /** Rows not yet rolled back, newest first, with their {@code old_state} to revert to. */
    public List<StateChange> selectForRollback(String dim, int cx, int cz, int radius,
                                               long sinceMillis, UUID playerFilter, int limit) {
        return selectChanges("old_state", 0, dim, cx, cz, radius, sinceMillis, playerFilter, limit);
    }

    /** Rows currently rolled back, newest first, with their {@code new_state} to re-apply. */
    public List<StateChange> selectForRestore(String dim, int cx, int cz, int radius,
                                              long sinceMillis, UUID playerFilter, int limit) {
        return selectChanges("new_state", 1, dim, cx, cz, radius, sinceMillis, playerFilter, limit);
    }

    private List<StateChange> selectChanges(String stateColumn, int rolledBack, String dim, int cx, int cz,
                                            int radius, long sinceMillis, UUID playerFilter, int limit) {
        // action IN (0,1) restricts to BREAK/PLACE; OBTAIN rows are not spatial changes.
        String sql = "SELECT id,x,y,z," + stateColumn + " FROM block_log "
                + "WHERE dim=? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ? AND time>=? AND rolled_back=? AND action IN (0,1)"
                + (playerFilter != null ? " AND player=?" : "")
                + " ORDER BY time DESC LIMIT ?";
        List<StateChange> out = new ArrayList<>();
        try (Connection conn = openRead(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = bindArea(ps, dim, cx, cz, radius, sinceMillis, null);
            ps.setInt(i++, rolledBack);
            if (playerFilter != null) {
                ps.setString(i++, playerFilter.toString());
            }
            ps.setInt(i, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new StateChange(rs.getLong("id"), rs.getInt("x"), rs.getInt("y"),
                            rs.getInt("z"), rs.getString(stateColumn)));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Block audit select failed: {}", e.getMessage(), e);
        }
        return out;
    }

    /** Binds dim, x-range, z-range, time, and (optionally) player. Returns next index. */
    private int bindArea(PreparedStatement ps, String dim, int cx, int cz, int radius,
                         long sinceMillis, UUID playerFilter) throws SQLException {
        int i = 1;
        ps.setString(i++, dim);
        ps.setInt(i++, cx - radius);
        ps.setInt(i++, cx + radius);
        ps.setInt(i++, cz - radius);
        ps.setInt(i++, cz + radius);
        ps.setLong(i++, sinceMillis);
        if (playerFilter != null) {
            ps.setString(i++, playerFilter.toString());
        }
        return i;
    }

    public void markRolledBack(Collection<Long> ids, boolean rolledBack) {
        if (ids.isEmpty()) {
            return;
        }
        try (Connection conn = connect("jdbc:sqlite:" + dbPath)) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("UPDATE block_log SET rolled_back=? WHERE id=?")) {
                for (long id : ids) {
                    ps.setInt(1, rolledBack ? 1 : 0);
                    ps.setLong(2, id);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to mark {} rows rolled_back={}: {}", ids.size(), rolledBack, e.getMessage(), e);
        }
    }
}
