package br.ufs.gothings.plugins.coap;

import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.plugin.PluginClient;
import br.ufs.gothings.core.plugin.PluginServer;
import br.ufs.gothings.core.plugin.ReplyLink;
import br.ufs.gothings.core.plugin.RequestLink;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Wagner Macedo
 */
public class CoapPlugin implements PluginClient, PluginServer {

    static final String GW_PROTOCOL = "coap";

    private final CaliforniumClient client;
    private final CaliforniumServer server;
    private final Settings settings;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private ReplyLink replyLink;
    private RequestLink requestLink;

    public CoapPlugin() {
        client = new CaliforniumClient();
        server = new CaliforniumServer();
        settings = new Settings(started);
    }

    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            client.start(replyLink);
            server.start(requestLink, settings);
        }
    }

    @Override
    public void stop() {
        if (started.compareAndSet(true, false)) {
            client.stop();
            server.stop();
        }
    }

    @Override
    public String getProtocol() {
        return GW_PROTOCOL;
    }

    @Override
    public Settings settings() {
        return settings;
    }

    /* Client implementation */

    @Override
    public void handleRequest(final GwRequest request) {
        client.sendRequest(request);
    }

    @Override
    public void setUp(final ReplyLink replyLink) {
        if (started.get()) {
            throw new IllegalStateException("plugin already started");
        }
        this.replyLink = replyLink;
    }

    /* Server implementation */

    @Override
    public void setUp(final RequestLink requestLink) {
        if (started.get()) {
            throw new IllegalStateException("plugin already started");
        }
        this.requestLink = requestLink;
    }
}
