package town.lampas.overrides;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Shared HttpClient instance for all outbound API calls.
 * A single HttpClient reuses one thread pool and connection pool,
 * instead of tripling resource usage across three separate instances.
 */
public final class HttpUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private HttpUtil() {}

    /**
     * Whether "offline mode" is active — i.e. every outbound HTTP call should be skipped so the mod
     * runs fully self-contained (offline / singleplayer / no network). Safe to call before the config
     * is loaded (returns false until then).
     */
    public static boolean isOfflineMode() {
        return ModConfig.SPEC.isLoaded() && ModConfig.OFFLINE_MODE.get();
    }

    /**
     * Guard for every outbound-HTTP call site: returns true when the call should be skipped because
     * offline mode is active. Callers should apply their normal "not configured" fallback (empty
     * result / cached data / failure callback) and return. Logs at debug only, so an intentionally
     * offline server doesn't spam the console.
     *
     * @param what short description of the skipped call, for debug logs
     */
    public static boolean skipHttpCall(String what) {
        if (isOfflineMode()) {
            LOGGER.debug("Offline mode active — skipping portal call: {}", what);
            return true;
        }
        return false;
    }
}
