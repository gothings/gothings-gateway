package br.ufs.gothings.plugins.mqtt;

import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.plugin.PluginClient;
import br.ufs.gothings.core.plugin.ReplyLink;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Wagner Macedo
 */
public class MqttPlugin implements PluginClient {

    static final String GW_PROTOCOL = "mqtt";

    private MqttPluginClient client;
    private final Settings settings;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private ReplyLink replyLink;

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
        }
    }

    @Override
    public void stop() {
        client = null;
        started.set(false);
    }

    @Override
    public void handleRequest(final GwRequest request) {
        try {
            client.sendRequest(request);
        } catch (MqttException ignored) {
        }
    }

    @Override
    public void setUp(final ReplyLink replyLink) {
        if (started.get()) {
            throw new IllegalStateException("plugin already started");
        }
        this.replyLink = replyLink;
    }

    @Override
    public String getProtocol() {
        return GW_PROTOCOL;
    }

    @Override
    public Settings settings() {
        return settings;
    }
}
