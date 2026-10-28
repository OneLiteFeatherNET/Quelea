package net.onelitefeather.quelea.backend.store.redis;

import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.micronaut.context.annotation.Requires;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import net.onelitefeather.quelea.backend.idempotency.IdempotencyKeys;
import net.onelitefeather.quelea.backend.idempotency.IdempotencyRecord;
import net.onelitefeather.quelea.backend.idempotency.IdempotencyStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;

/**
 * Idempotency records in Dragonfly under {@code idem:<client>:<sha256 of the key>}, as JSON with a time to live. The
 * claim is one {@code SET NX PX}, so two requests with the same key cannot both store the marker.
 */
@Singleton
@Requires(beans = StatefulRedisConnection.class)
public class RedisIdempotencyStore implements IdempotencyStore {

    /** A marker can expire between the failed SET NX and the GET; the claim is then tried again. */
    private static final int CLAIM_ATTEMPTS = 3;

    private final RedisCommands<String, String> commands;
    private final RedisKeys keys;
    private final ObjectMapper json;

    public RedisIdempotencyStore(StatefulRedisConnection<String, String> connection, RedisKeys keys, ObjectMapper json) {
        this.commands = connection.sync();
        this.keys = keys;
        this.json = json;
    }

    @Override
    public Optional<IdempotencyRecord> claim(String client, String key, IdempotencyRecord marker, Duration ttl) {
        String redisKey = keys.idempotency(client, IdempotencyKeys.hash(key));
        String value = write(marker);
        for (int attempt = 0; attempt < CLAIM_ATTEMPTS; attempt++) {
            if ("OK".equals(commands.set(redisKey, value, SetArgs.Builder.nx().px(ttl.toMillis())))) {
                return Optional.empty();
            }
            String current = commands.get(redisKey);
            if (current != null) {
                return Optional.of(read(current));
            }
        }
        // The key keeps vanishing between the two commands: report it as in progress, which the client may retry.
        return Optional.of(marker);
    }

    @Override
    public void complete(String client, String key, IdempotencyRecord completed, Duration ttl) {
        commands.set(keys.idempotency(client, IdempotencyKeys.hash(key)), write(completed),
                SetArgs.Builder.px(ttl.toMillis()));
    }

    @Override
    public void release(String client, String key) {
        commands.del(keys.idempotency(client, IdempotencyKeys.hash(key)));
    }

    private String write(IdempotencyRecord record) {
        try {
            return json.writeValueAsString(record);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot serialise the idempotency record", e);
        }
    }

    private IdempotencyRecord read(String value) {
        try {
            return json.readValue(value, IdempotencyRecord.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the idempotency record", e);
        }
    }
}
