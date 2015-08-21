package br.ufs.gothings.plugins.mqtt;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.GwPlugin;
import br.ufs.gothings.core.PluginSettings;
import br.ufs.gothings.core.sink.Sink;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Wagner Macedo
 */
public class MqttPlugin implements GwPlugin {

    static final String GW_PROTOCOL = "mqtt";

    private final MqttPluginClient client;
    private final PluginSettings settings;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Sink<GwMessage> cliSink;
    private final Sink<GwMessage> srvSink;

    public MqttPlugin() {
        cliSink = new Sink<>();
        srvSink = new Sink<>();
        settings = new PluginSettings(started, GW_PROTOCOL);

        client = new MqttPluginClient(cliSink);
    }

    @Override
    public void start() {
        started.set(true);
    }

    @Override
    public void stop() {
        cliSink.stop();
        srvSink.stop();
        started.set(false);
    }

    @Override
    public Sink<GwMessage> clientSink() {
        return cliSink;
    }

    @Override
    public Sink<GwMessage> serverSink() {
        return srvSink;
    }

    @Override
    public PluginSettings settings() {
        return settings;
    }
}
