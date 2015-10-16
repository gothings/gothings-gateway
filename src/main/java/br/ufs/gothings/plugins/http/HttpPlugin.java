package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.message.GwError;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.plugin.PluginServer;
import br.ufs.gothings.core.plugin.RequestLink;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Wagner Macedo
 */
public class HttpPlugin implements PluginServer {

    static final String GW_PROTOCOL = "http";

    private final HttpPluginServer server;
    private final Settings settings;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private RequestLink requestLink;

    public HttpPlugin() {
        server = new ApacheHCServer();
        settings = new Settings(started);
    }

    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            if (requestLink == null) {
                throw new NullPointerException("no RequestLink to start the server");
            }
            try {
                server.start(requestLink, settings);
            } catch (InterruptedException e) {
                started.set(false);
            }
        }
    }

    @Override
    public void stop() {
        if (started.compareAndSet(true, false)) {
            try {
                server.stop();
            } catch (InterruptedException ignored) {
            }
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

    /* Server implementation */

    @Override
    public void handleReply(final GwReply reply) {
        // do nothing => this plugin doesn't handle independent replies
    }

    @Override
    public void handleError(final GwError error) {
        // do nothing => this plugin doesn't handle independent errors
    }

    @Override
    public void setUp(final RequestLink requestLink) {
        if (started.get()) {
            throw new IllegalStateException("plugin already started");
        }
        this.requestLink = requestLink;
    }
}
