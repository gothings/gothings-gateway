package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.GwPlugin;
import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.sink.Sink;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Wagner Macedo
 */
public class HttpPlugin implements GwPlugin {

    static final String GW_PROTOCOL = "http";

    private final HttpPluginServer server;
    private final Settings settings;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Sink<GwMessage> cliSink;
    private final Sink<GwMessage> srvSink;

    public HttpPlugin() {
        server = new HttpPluginServer();
        cliSink = null;
        srvSink = new Sink<>();
        settings = new Settings(started);
    }

    @Override
    public void start() {
        try {
            started.set(true);
            server.start(srvSink, (Integer) settings.get("server.port"));
        } catch (InterruptedException ignored) {
            started.set(false);
        }
    }

    @Override
    public void stop() {
        try {
            server.stop();
            cliSink.stop();
            srvSink.stop();
            started.set(false);
        } catch (InterruptedException ignored) {
        }
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
    public String getProtocol() {
        return GW_PROTOCOL;
    }

    @Override
    public Settings settings() {
        return settings;
    }
}
