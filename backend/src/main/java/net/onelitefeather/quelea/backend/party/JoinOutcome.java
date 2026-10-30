package net.onelitefeather.quelea.backend.party;

/** What a player's request to join a party led to: a direct join into a public party, or an open request to a private one. */
public sealed interface JoinOutcome permits JoinOutcome.Joined, JoinOutcome.Requested {

    /** The player joined directly, because the party is public. */
    record Joined(Party party) implements JoinOutcome {
    }

    /** The request is open until its expiry and waits for the leader's answer. */
    record Requested(JoinRequest request) implements JoinOutcome {
    }
}
