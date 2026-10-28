package net.onelitefeather.quelea.backend.presence;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("quelea.presence")
public class PresenceSettings {

    private Duration proxyTimeout = Duration.ofSeconds(45);
    private Duration reaperInterval = Duration.ofSeconds(30);

    /** A proxy without a heartbeat for this long is dead; its players are disconnected. */
    public Duration getProxyTimeout() {
        return proxyTimeout;
    }

    public void setProxyTimeout(Duration proxyTimeout) {
        this.proxyTimeout = proxyTimeout;
    }

    /** How often the dead-proxy sweep runs. */
    public Duration getReaperInterval() {
        return reaperInterval;
    }

    public void setReaperInterval(Duration reaperInterval) {
        this.reaperInterval = reaperInterval;
    }
}
