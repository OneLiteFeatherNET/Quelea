package net.onelitefeather.quelea.velocity.stream;

/**
 * One server-sent event as the parser assembled it.
 *
 * @param id    the {@code id:} field, empty when absent
 * @param event the {@code event:} field, empty when absent
 * @param data  the {@code data:} lines joined with newlines
 */
public record SseMessage(String id, String event, String data) {
}
