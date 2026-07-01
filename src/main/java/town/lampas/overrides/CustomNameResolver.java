package town.lampas.overrides;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Resolves a player's full custom name as produced by the {@code eclipsescustomname} mod
 * (prefix + nickname/name + suffix, including any LuckPerms prefix/suffix).
 *
 * <p>{@code eclipsescustomname} is a Fabric mod loaded alongside this NeoForge mod via Sinytra
 * Connector, so we can't depend on it at compile time. We reach its
 * {@code PlayerNameManager#getFullPlayerName(ServerPlayer)} reflectively (Connector remaps the
 * Fabric mod's Yarn references to the runtime mappings, but leaves the mod's own class/method names
 * intact). We deliberately avoid {@link ServerPlayer#getDisplayName()} here: NeoForge caches that
 * value <em>after</em> our own {@code NameFormat} handlers have prepended the {@code [LIVE]} and
 * {@code [he/him]} prefixes (see {@link LiveStatusHandler}/{@link PronounStatusHandler}), which are
 * not part of the customname-resolved name.
 *
 * <p>If the mod is absent (e.g. dev runs) or its API changes, every lookup falls back to the
 * vanilla account name and the reflection is disabled after the first failure.
 */
public final class CustomNameResolver {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String CUSTOM_NAME_CLASS = "xyz.eclipseisoffline.eclipsescustomname.CustomName";
    private static final String MANAGER_CLASS = "xyz.eclipseisoffline.eclipsescustomname.PlayerNameManager";

    private static volatile boolean unavailable = false;
    private static Method getConfig;
    private static Method getPlayerNameManager;
    private static Method getFullPlayerName;

    static {
        try {
            Class<?> customName = Class.forName(CUSTOM_NAME_CLASS);
            Class<?> manager = Class.forName(MANAGER_CLASS);
            getConfig = customName.getMethod("getConfig");
            Class<?> configClass = getConfig.getReturnType();
            getPlayerNameManager = manager.getMethod("getPlayerNameManager", MinecraftServer.class, configClass);
            getFullPlayerName = manager.getMethod("getFullPlayerName", ServerPlayer.class);
        } catch (Throwable t) {
            unavailable = true;
            LOGGER.info("eclipsescustomname unavailable for custom-name resolution; using vanilla names ({})",
                    t.toString());
        }
    }

    private CustomNameResolver() {}

    /**
     * Returns the player's full custom name, or their vanilla account name if customname can't be
     * resolved. Must be called on the server thread — {@code PlayerNameManager} reads non-concurrent
     * state.
     */
    public static String resolve(ServerPlayer player) {
        String fallback = player.getName().getString();
        if (unavailable) return fallback;
        try {
            MinecraftServer server = player.server;
            Object config = getConfig.invoke(null);
            Object manager = getPlayerNameManager.invoke(null, server, config);
            Object name = getFullPlayerName.invoke(manager, player);
            if (name instanceof Component component) {
                String resolved = component.getString();
                if (resolved != null && !resolved.isBlank()) return resolved;
            }
            return fallback;
        } catch (Throwable t) {
            unavailable = true;
            LOGGER.info("eclipsescustomname unavailable for custom-name resolution; using vanilla names ({})",
                    t.toString());
            return fallback;
        }
    }
}
