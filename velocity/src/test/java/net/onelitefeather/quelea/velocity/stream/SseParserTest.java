package net.onelitefeather.quelea.velocity.stream;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseParserTest {

    private final List<SseMessage> messages = new ArrayList<>();
    private final SseParser parser = new SseParser(messages::add);

    private void feed(String... lines) {
        for (String line : lines) {
            parser.accept(line);
        }
    }

    @Test
    void dispatchesIdEventAndDataOnBlankLine() {
        feed("id: 42", "event: party.joined", "data: {\"a\":1}", "");

        assertEquals(List.of(new SseMessage("42", "party.joined", "{\"a\":1}")), messages);
    }

    @Test
    void joinsMultipleDataLinesWithNewlines() {
        feed("data: first", "data: second", "data: third", "");

        assertEquals("first\nsecond\nthird", messages.get(0).data());
    }

    @Test
    void ignoresKeepAliveComments() {
        feed(": keep-alive", "", ": another comment", "data: x", "");

        assertEquals(1, messages.size(), "only the data message is dispatched");
        assertEquals("x", messages.get(0).data());
    }

    @Test
    void dispatchesNothingWithoutData() {
        feed("id: 7", "event: party.created", "");

        assertTrue(messages.isEmpty(), "an event without data is not dispatched");
    }

    @Test
    void doesNotDispatchUntilTheBlankLine() {
        feed("id: 1", "data: pending");

        assertTrue(messages.isEmpty(), "the message is complete only at the blank line");
    }

    @Test
    void resetsFieldsBetweenMessages() {
        feed("id: 1", "event: party.chat", "data: one", "", "data: two", "");

        assertEquals(new SseMessage("1", "party.chat", "one"), messages.get(0));
        assertEquals(new SseMessage("", "", "two"), messages.get(1));
    }

    @Test
    void stripsOnlyOneLeadingSpaceFromTheValue() {
        feed("data:  indented", "");

        assertEquals(" indented", messages.get(0).data());
    }
}
