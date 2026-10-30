package net.onelitefeather.quelea.backend.store.redis;

import io.micronaut.serde.ObjectMapper;
import net.onelitefeather.quelea.backend.party.JoinRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The join-request, cooldown and ban keys in Dragonfly, including their cleanup when a party is disbanded. */
@Tag("integration")
class RedisJoinRequestStoreTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private static DragonflyFixture dragonfly;

    private RedisPartyStore store;

    @BeforeAll
    static void startDragonfly() {
        dragonfly = DragonflyFixture.start();
    }

    @AfterAll
    static void stopDragonfly() {
        if (dragonfly != null) {
            dragonfly.close();
        }
    }

    @BeforeEach
    void freshStore() {
        dragonfly.flushAll();
        store = new RedisPartyStore(dragonfly.connection(), new RedisKeys("quelea:"), ObjectMapper.getDefault());
    }

    @Test
    void savedJoinRequestCarriesAnExpiringKeyUnderTheRequestKey() {
        UUID party = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        JoinRequest request = new JoinRequest(party, requester, "Bravo", NOW.plus(Duration.ofSeconds(60)));

        store.saveJoinRequest(request, Duration.ofSeconds(60));

        assertEquals(request, store.findJoinRequest(party, requester).orElseThrow());
        Long pttl = dragonfly.connection().sync().pttl("quelea:join-request:" + party + ":" + requester);
        assertTrue(pttl != null && pttl > 0 && pttl <= 60_000L, "the request key must expire with the request TTL, was " + pttl);
    }

    @Test
    void rejectionOverwritesTheCooldownWithTheShorterWindow() {
        UUID party = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        store.setCooldown(party, requester, Duration.ofSeconds(120));

        store.setCooldown(party, requester, Duration.ofSeconds(60));

        assertTrue(store.cooldownLeft(party, requester).compareTo(Duration.ofSeconds(60)) <= 0,
                "a rejection must start a fresh cooldown of the cooldown length, not keep the longer one");
    }

    @Test
    void acceptedRequestClearsItsCooldown() {
        UUID party = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        store.setCooldown(party, requester, Duration.ofSeconds(60));

        store.clearCooldown(party, requester);

        assertTrue(store.cooldownLeft(party, requester).isZero(), "an accepted request must leave no cooldown");
    }

    @Test
    void deleteRequestsOfRemovesOpenRequestsAndCooldownsOfTheParty() {
        UUID party = UUID.randomUUID();
        UUID requester = UUID.randomUUID();
        store.saveJoinRequest(new JoinRequest(party, requester, "Bravo", NOW.plus(Duration.ofSeconds(60))), Duration.ofSeconds(60));
        store.setCooldown(party, requester, Duration.ofSeconds(120));

        store.deleteRequestsOf(party);

        assertTrue(store.findJoinRequest(party, requester).isEmpty(), "disband must remove the open request");
        assertTrue(store.cooldownLeft(party, requester).isZero(), "disband must remove the cooldown");
        assertFalse(dragonfly.connection().sync().exists("quelea:party:" + party + ":requesters") > 0,
                "disband must remove the requester index");
    }

    @Test
    void deleteBansRemovesTheBanSetOfTheParty() {
        UUID party = UUID.randomUUID();
        UUID kicked = UUID.randomUUID();
        store.ban(party, kicked);
        assertTrue(store.isBanned(party, kicked));

        store.deleteBans(party);

        assertFalse(store.isBanned(party, kicked), "disband must remove the ban set");
        assertFalse(dragonfly.connection().sync().exists("quelea:party:" + party + ":bans") > 0);
    }

    @Test
    void unbanLiftsOnlyTheInvitedPlayer() {
        UUID party = UUID.randomUUID();
        UUID invited = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        store.ban(party, invited);
        store.ban(party, other);

        store.unban(party, invited);

        assertFalse(store.isBanned(party, invited));
        assertTrue(store.isBanned(party, other));
    }
}
