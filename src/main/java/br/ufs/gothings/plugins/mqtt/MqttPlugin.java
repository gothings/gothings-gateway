package br.ufs.gothings.plugins.mqtt;

import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.plugin.PluginClient;
import br.ufs.gothings.core.plugin.PluginServer;
import br.ufs.gothings.core.plugin.ReplyLink;
import br.ufs.gothings.core.plugin.RequestLink;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Wagner Macedo
 */
public class MqttPlugin implements PluginClient, PluginServer {

    static final String GW_PROTOCOL = "mqtt";

    private MqttPluginClient client;
    private MqttPluginServer server;
    private final Settings settings;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private ReplyLink replyLink;
    private RequestLink requestLink;

    public MqttPlugin() {
        settings = new Settings(started);
    }

    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            if (replyLink == null) {
                throw new NullPointerException("no ReplyLink to start the server");
            }
            client = new MqttPluginClient(replyLink);
            server = new MqttPluginServer(requestLink, settings);
            server.start();
        }
    }

    @Override
    public void stop() {
        client.close();
        client = null;
        server.stop();
        server = null;
        started.set(false);
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
        this.requestLink = requestLink;
    }
}
