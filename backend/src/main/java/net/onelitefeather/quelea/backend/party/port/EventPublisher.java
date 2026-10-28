package net.onelitefeather.quelea.backend.party.port;

import net.onelitefeather.quelea.backend.event.PartyEvent;

public interface EventPublisher {
    void publish(PartyEvent event);
}
