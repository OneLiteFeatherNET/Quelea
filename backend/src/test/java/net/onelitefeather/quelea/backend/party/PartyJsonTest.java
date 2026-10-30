package net.onelitefeather.quelea.backend.party;

import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The stored form of a party, read and written through the same mapper the Redis store uses. */
class PartyJsonTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T12:00:00Z");
    private static final UUID PARTY = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID LEADER = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private final ObjectMapper json = ObjectMapper.getDefault();

    @Test
    void partyStoredBeforeTheAloneSinceFieldReadsItAsNullAndStartsAtCreation() throws Exception {
        // The shape 0.4.0 wrote: no aloneSince key at all.
        String legacy = """
                {"id":"%s","leaderId":"%s","members":[{"id":"%s","name":"Alpha","joinedAt":"%s"}],\
                "createdAt":"%s","version":3}""".formatted(PARTY, LEADER, LEADER, CREATED, CREATED);

        Party party = json.readValue(legacy, Party.class);

        assertNull(party.aloneSince(), "a party without the key must read the alone-since time as null");
        assertEquals(CREATED, party.aloneStart(), "without an alone-since time, the alone period starts at creation");
        assertEquals(3L, party.version(), "the rest of the stored party must read unchanged");
    }

    @Test
    void aloneSinceSurvivesAWriteAndARead() throws Exception {
        Instant departure = CREATED.plusSeconds(600);
        Party alone = new Party(PARTY, LEADER, List.of(new Member(LEADER, "Alpha", CREATED)), CREATED, 4, departure);

        Party read = json.readValue(json.writeValueAsString(alone), Party.class);

        assertEquals(departure, read.aloneSince(), "the alone-since time must come back unchanged");
        assertEquals(departure, read.aloneStart(), "the alone period must start at the departure, not at creation");
    }
}
