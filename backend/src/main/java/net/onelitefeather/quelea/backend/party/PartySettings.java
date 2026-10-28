package net.onelitefeather.quelea.backend.party;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.validation.Validated;
import jakarta.validation.constraints.Min;

import java.time.Duration;

@Validated
@ConfigurationProperties("quelea.party")
public class PartySettings {

    private int maxSize = 8;
    private Duration inviteTtl = Duration.ofSeconds(60);

    /** Maximum number of members per party, counting the leader. */
    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(@Min(2) int maxSize) {
        this.maxSize = maxSize;
    }

    /** How long an invite stays valid. */
    public Duration getInviteTtl() {
        return inviteTtl;
    }

    public void setInviteTtl(Duration inviteTtl) {
        this.inviteTtl = inviteTtl;
    }
}
