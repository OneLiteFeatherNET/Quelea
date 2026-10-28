package net.onelitefeather.quelea.backend.store.redis;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;

/** Lua scripts that must check and modify a key in one atomic step. */
final class RedisScripts {

    /** Deletes KEYS[1] only if it still holds ARGV[1]. Returns 1 when deleted, 0 otherwise. */
    static final String COMPARE_AND_DELETE =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end";

    private RedisScripts() {
    }

    static boolean compareAndDelete(RedisCommands<String, String> commands, String key, String expected) {
        Long deleted = commands.eval(COMPARE_AND_DELETE, ScriptOutputType.INTEGER, new String[]{key}, expected);
        return deleted != null && deleted == 1L;
    }
}
