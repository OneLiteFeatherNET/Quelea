package net.onelitefeather.quelea.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.onelitefeather.quelea.velocity.auth.ClientCredentialsTokenProvider;
import net.onelitefeather.quelea.velocity.auth.JdkTokenTransport;
import net.onelitefeather.quelea.velocity.command.PartyActions;
import net.onelitefeather.quelea.velocity.command.PartyCommands;
import net.onelitefeather.quelea.velocity.config.QueleaConfig;
import net.onelitefeather.quelea.velocity.event.EventHandler;
import net.onelitefeather.quelea.velocity.event.PartyEvent;
import net.onelitefeather.quelea.velocity.follow.FollowPolicy;
import net.onelitefeather.quelea.velocity.http.HttpQueleaGateway;
import net.onelitefeather.quelea.velocity.http.PlayerRef;
import net.onelitefeather.quelea.velocity.http.QueleaGateway;
import net.onelitefeather.quelea.velocity.http.QueleaProblemException;
import net.onelitefeather.quelea.velocity.http.QueleaUnavailableException;
import net.onelitefeather.quelea.velocity.i18n.Messages;
import net.onelitefeather.quelea.velocity.listener.PresenceListener;
import net.onelitefeather.quelea.velocity.player.LocalPlayers;
import net.onelitefeather.quelea.velocity.player.VelocityLocalPlayers;
import net.onelitefeather.quelea.velocity.stream.Backoff;
import net.onelitefeather.quelea.velocity.stream.EventStream;
import net.onelitefeather.quelea.velocity.stream.Sleeper;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Velocity entry point. Wires the configuration, the party service client, the event stream, the heartbeat, the
 * commands and the listeners together. The class is named in {@code velocity-plugin.json}.
 */
public final class QueleaVelocity {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private ExecutorService executor;
    private HttpClient httpClient;
    private EventStream eventStream;
    private ScheduledTask heartbeat;

    @Inject
    public QueleaVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        QueleaConfig config;
        try {
            config = QueleaConfig.load(dataDirectory.resolve(QueleaConfig.FILE_NAME), System::getenv);
        } catch (IOException | IllegalArgumentException e) {
            logger.error("Quelea is disabled: the configuration could not be loaded: {}", e.getMessage());
            return;
        }

        Optional<ClientCredentialsTokenProvider> tokens = Optional.empty();
        if (config.credentials().isComplete()) {
            tokens = Optional.of(new ClientCredentialsTokenProvider(config.credentials(), new JdkTokenTransport(),
                    Clock.systemUTC()));
        } else {
            logger.warn("Quelea has no complete Entra credentials (missing {}): no token is sent, so only a dev "
                    + "party service accepts this plugin", config.credentials().missing());
        }

        executor = Executors.newVirtualThreadPerTaskExecutor();
        httpClient = HttpQueleaGateway.newClient();
        QueleaGateway gateway = new HttpQueleaGateway(config.url(), httpClient, tokens);
        Messages messages = Messages.load();
        FollowPolicy follow = FollowPolicy.of(config.lobbyPattern(), config.followMode());
        VelocityLocalPlayers localPlayers = new VelocityLocalPlayers(proxy);

        proxy.getEventManager().register(this,
                new PresenceListener(gateway, executor, config.proxyId(), follow, logger));

        PartyActions actions = new PartyActions(gateway, executor, messages, logger);
        PartyCommands commands = new PartyCommands(actions, localPlayers, localPlayers::wrap, messages);
        registerCommand(commands.party(), "p");
        registerCommand(commands.chat());

        EventHandler handler = new EventHandler(localPlayers, messages, follow, Clock.systemUTC());
        eventStream = new EventStream(
                httpClient,
                HttpQueleaGateway.eventsUri(config.url(), config.proxyId()),
                tokens,
                () -> gateway.snapshot(config.proxyId(), snapshot(localPlayers)),
                message -> handler.handle(PartyEvent.parse(message.data())),
                new Backoff(Duration.ofSeconds(1), Duration.ofSeconds(30)),
                Sleeper.SYSTEM,
                logger);
        eventStream.start();

        heartbeat = proxy.getScheduler()
                .buildTask(this, () -> executor.execute(() -> sendHeartbeat(gateway, config.proxyId())))
                .repeat(HEARTBEAT_INTERVAL)
                .schedule();

        logger.info("Quelea party plugin started for proxy {} against {}", config.proxyId(), config.url());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (heartbeat != null) {
            heartbeat.cancel();
        }
        if (eventStream != null) {
            eventStream.stop();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void registerCommand(BrigadierCommand command, String... aliases) {
        CommandManager commands = proxy.getCommandManager();
        commands.register(commands.metaBuilder(command).aliases(aliases).plugin(this).build(), command);
    }

    private static List<PlayerRef> snapshot(LocalPlayers players) {
        return players.all().stream()
                .map(player -> new PlayerRef(player.id().toString(), player.name()))
                .toList();
    }

    private void sendHeartbeat(QueleaGateway gateway, String proxyId) {
        try {
            gateway.heartbeat(proxyId);
        } catch (QueleaProblemException | QueleaUnavailableException e) {
            logger.warn("Heartbeat failed: {}", e.getMessage());
        }
    }
}
