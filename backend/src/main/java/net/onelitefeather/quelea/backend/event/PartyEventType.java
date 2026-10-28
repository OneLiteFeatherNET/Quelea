package net.onelitefeather.quelea.backend.event;

public enum PartyEventType {
    CREATED("party.created"),
    INVITED("party.invited"),
    JOINED("party.joined"),
    DENIED("party.denied"),
    LEFT("party.left"),
    KICKED("party.kicked"),
    LEADER_CHANGED("party.leader-changed"),
    DISBANDED("party.disbanded"),
    WARP("party.warp"),
    CHAT("party.chat");

    private final String wireName;

    PartyEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
