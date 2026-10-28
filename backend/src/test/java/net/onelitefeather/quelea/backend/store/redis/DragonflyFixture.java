package net.onelitefeather.quelea.backend.store.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * One Dragonfly container with a Lettuce connection, owned by one test class. Each test class starts its own
 * fixture in {@code @BeforeAll} and closes it in {@code @AfterAll}; tests clear the data with {@link #flushAll()}.
 */
final class DragonflyFixture implements AutoCloseable {

    /** Same tag as docker/docker-compose.yml. */
    static final String IMAGE = "docker.dragonflydb.io/dragonflydb/dragonfly:v1.27.1";

    private final GenericContainer<?> container;
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;

    private DragonflyFixture(GenericContainer<?> container) {
        this.container = container;
        this.client = RedisClient.create("redis://" + container.getHost() + ":" + container.getMappedPort(6379));
        this.connection = client.connect();
    }

    static DragonflyFixture start() {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(IMAGE)).withExposedPorts(6379);
        container.start();
        return new DragonflyFixture(container);
    }

    StatefulRedisConnection<String, String> connection() {
        return connection;
    }

    void flushAll() {
        connection.sync().flushall();
    }

    /**
     * Polls {@code condition} until it holds or {@code timeout} passes. Used only to wait for Redis key expiry,
     * which is driven by the server clock.
     */
    static boolean awaitTrue(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
        }
        return true;
    }

    @Override
    public void close() {
        connection.close();
        client.shutdown();
        container.stop();
    }
}
