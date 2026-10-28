package net.onelitefeather.quelea.backend.support;

import net.onelitefeather.quelea.backend.event.PartyEvent;
import net.onelitefeather.quelea.backend.party.port.EventPublisher;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingEventPublisher implements EventPublisher {

    private final List<PartyEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void publish(PartyEvent event) {
        events.add(event);
    }

    public List<PartyEvent> events() {
        return List.copyOf(events);
    }

    public List<String> types() {
        return events.stream().map(PartyEvent::type).toList();
    }
}
