package net.onelitefeather.quelea.velocity.stream;

import java.util.function.Consumer;

/**
 * Turns the lines of a {@code text/event-stream} into {@link SseMessage}s. Feed it one line at a time; a blank
 * line dispatches the message collected so far. Lines starting with a colon are keep-alive comments and ignored.
 * Not thread-safe: one parser per connection.
 */
public final class SseParser {

    private final Consumer<SseMessage> sink;
    private final StringBuilder data = new StringBuilder();
    private String id = "";
    private String event = "";
    private boolean hasData;

    public SseParser(Consumer<SseMessage> sink) {
        this.sink = sink;
    }

    /**
     * @param line one line of the stream, without its line terminator
     */
    public void accept(String line) {
        if (line.isEmpty()) {
            dispatch();
            return;
        }
        if (line.startsWith(":")) {
            return;
        }
        int colon = line.indexOf(':');
        String field = colon < 0 ? line : line.substring(0, colon);
        String value = colon < 0 ? "" : line.substring(colon + 1);
        if (value.startsWith(" ")) {
            value = value.substring(1);
        }
        switch (field) {
            case "id" -> id = value;
            case "event" -> event = value;
            case "data" -> {
                if (hasData) {
                    data.append('\n');
                }
                data.append(value);
                hasData = true;
            }
            default -> {
                // retry: and unknown fields carry nothing this plugin needs.
            }
        }
    }

    private void dispatch() {
        if (hasData) {
            sink.accept(new SseMessage(id, event, data.toString()));
        }
        data.setLength(0);
        hasData = false;
        id = "";
        event = "";
    }
}
