package net.onelitefeather.quelea.backend.party;

import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyPublicJsonTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T12:00:00Z");
    private static final UUID PARTY = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID LEADER = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private final ObjectMapper json = ObjectMapper.getDefault();

    @Test
    void publicFlagIsWrittenUnderThePublicKey() throws Exception {
        Party open = new Party(PARTY, LEADER, List.of(new Member(LEADER, "Alpha", CREATED)), CREATED, 2, null, true);

        String written = json.writeValueAsString(open);

        assertTrue(written.contains("\"public\":true"), "the open flag must be written as \"public\": " + written);
    }

    @Test
    void partyStoredBeforeThePublicFlagReadsAsPrivate() throws Exception {
        String legacy = """
                {"id":"%s","leaderId":"%s","members":[{"id":"%s","name":"Alpha","joinedAt":"%s"}],\
                "createdAt":"%s","version":3}""".formatted(PARTY, LEADER, LEADER, CREATED, CREATED);

        Party party = json.readValue(legacy, Party.class);

        assertFalse(party.publicParty(), "a party stored without the flag must read as private");
    }
}
