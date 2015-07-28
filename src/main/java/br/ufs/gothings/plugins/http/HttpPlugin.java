package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.CommunicationManager;
import br.ufs.gothings.core.GwPlugin;
import br.ufs.gothings.core.PluginSettings;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Wagner Macedo
 */
public class HttpPlugin implements GwPlugin {

    private final HttpPluginServer server;
    private final PluginSettings settings;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public HttpPlugin() {
        server = new HttpPluginServer();
        settings = new PluginSettings(started);
    }

    @Override
    public void start(CommunicationManager manager) {
        try {
            started.set(true);
            server.start(manager, settings.getPort());
        } catch (InterruptedException ignored) {
            started.set(false);
        }
    }

    @Override
    public void stop() {
        try {
            server.stop();
            started.set(false);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public PluginSettings settings() {
        return settings;
    }
}
