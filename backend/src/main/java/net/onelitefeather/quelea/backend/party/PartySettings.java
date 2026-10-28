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
    private Duration aloneTimeout = Duration.ofMinutes(30);
    private Duration aloneWarning = Duration.ofMinutes(5);
    private Duration expiryInterval = Duration.ofSeconds(15);

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

    /** How long a party may have only its creator as member before it is disbanded. */
    public Duration getAloneTimeout() {
        return aloneTimeout;
    }

    public void setAloneTimeout(Duration aloneTimeout) {
        this.aloneTimeout = aloneTimeout;
    }

    /** How long before the alone timeout party.expiring is published; zero disables the warning. */
    public Duration getAloneWarning() {
        return aloneWarning;
    }

    public void setAloneWarning(Duration aloneWarning) {
        this.aloneWarning = aloneWarning;
    }

    /** How often the expiry job runs. */
    public Duration getExpiryInterval() {
        return expiryInterval;
    }

    public void setExpiryInterval(Duration expiryInterval) {
        this.expiryInterval = expiryInterval;
    }
}
