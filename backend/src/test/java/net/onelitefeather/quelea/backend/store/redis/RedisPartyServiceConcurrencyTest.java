package net.onelitefeather.quelea.backend.store.redis;

import io.micronaut.serde.ObjectMapper;
import net.onelitefeather.quelea.backend.party.Party;
import net.onelitefeather.quelea.backend.party.PartyService;
import net.onelitefeather.quelea.backend.party.PartySettings;
import net.onelitefeather.quelea.backend.presence.PlayerPresence;
import net.onelitefeather.quelea.backend.problem.PartyException;
import net.onelitefeather.quelea.backend.problem.PartyProblem;
import net.onelitefeather.quelea.backend.support.MutableClock;
import net.onelitefeather.quelea.backend.support.RecordingEventPublisher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The PartyService running on the Dragonfly adapters: the "Concurrent accepts" scenario of the party spec. */
@Tag("integration")
class RedisPartyServiceConcurrencyTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private static DragonflyFixture dragonfly;

    private MutableClock clock;
    private RedisPartyStore parties;
    private RedisPresenceStore presence;
    private PartyService service;
    private UUID leaderA;
    private UUID leaderB;
    private UUID charlie;

    @BeforeAll
    static void startDragonfly() {
        dragonfly = DragonflyFixture.start();
    }

    @AfterAll
    static void stopDragonfly() {
        dragonfly.close();
    }

    @BeforeEach
    void freshService() {
        dragonfly.flushAll();
        RedisKeys keys = new RedisKeys("quelea:");
        clock = new MutableClock(NOW);
        parties = new RedisPartyStore(dragonfly.connection(), keys, ObjectMapper.getDefault());
        presence = new RedisPresenceStore(dragonfly.connection(), keys, ObjectMapper.getDefault(), clock);
        RedisLockService locks = new RedisLockService(dragonfly.connection(), keys,
                Duration.ofSeconds(3), Duration.ofSeconds(1));
        service = new PartyService(parties, presence, locks, new RecordingEventPublisher(), new PartySettings(), clock);
        leaderA = online("Alpha");
        leaderB = online("Bravo");
        charlie = online("Charlie");
    }

    @Test
    void concurrentAcceptsLetExactlyOneSucceed() throws Exception {
        Party partyA = service.create(leaderA);
        Party partyB = service.create(leaderB);
        service.invite(leaderA, "Charlie");
        service.invite(leaderB, "Charlie");

        List<Object> outcomes = acceptConcurrently(partyA.id(), partyB.id());

        long successes = outcomes.stream().filter(Party.class::isInstance).count();
        assertEquals(1, successes, "exactly one accept should succeed, outcomes: " + outcomes);
        assertTrue(outcomes.contains(PartyProblem.ALREADY_IN_PARTY),
                "the other accept should be answered with already-in-party, outcomes: " + outcomes);
        long partiesContainingCharlie = List.of(partyA.id(), partyB.id()).stream()
                .filter(id -> service.get(id).isMember(charlie))
                .count();
        assertEquals(1, partiesContainingCharlie, "Charlie should be a member of exactly one party");
        assertTrue(service.partyOf(charlie).isMember(charlie));
    }

    /** Both accepts start together; each returns the joined party or the problem it was refused with. */
    private List<Object> acceptConcurrently(UUID firstParty, UUID secondParty) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (UUID partyId : List.of(firstParty, secondParty)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return service.accept(charlie, partyId);
                    } catch (PartyException e) {
                        return e.problem();
                    }
                }));
            }
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(10, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private UUID online(String name) {
        UUID id = UUID.randomUUID();
        presence.put(new PlayerPresence(id, name, "proxy-1", clock.instant()));
        return id;
    }
}
