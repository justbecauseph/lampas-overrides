package town.lampas.overrides;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Shared HttpClient instance for all outbound API calls.
 * A single HttpClient reuses one thread pool and connection pool,
 * instead of tripling resource usage across three separate instances.
 */
public final class HttpUtil {
    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private HttpUtil() {}
}
