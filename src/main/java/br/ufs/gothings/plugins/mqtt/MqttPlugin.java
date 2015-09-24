package br.ufs.gothings.plugins.mqtt;

import br.ufs.gothings.core.plugin.GwPlugin;
import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.message.sink.MessageSink;
import br.ufs.gothings.core.message.sink.MessageLink;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Wagner Macedo
 */
public class MqttPlugin implements GwPlugin {

    static final String GW_PROTOCOL = "mqtt";

    private final MqttPluginClient client;
    private final Settings settings;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final MessageSink cliSink = new MessageSink();

    public MqttPlugin() {
        settings = new Settings(started);
        client = new MqttPluginClient(cliSink.getRightLink());
    }

    @Override
    public void start() {
        started.set(true);
    }

    @Override
    public void stop() {
        cliSink.stop();
        started.set(false);
    }

    @Override
    public MessageLink clientLink() {
        return cliSink.getLeftLink();
    }

    @Override
    public MessageLink serverLink() {
        return null;
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
