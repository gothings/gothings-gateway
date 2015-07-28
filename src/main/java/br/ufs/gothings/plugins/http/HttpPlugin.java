package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.GwPlugin;

/**
 * @author Wagner Macedo
 */
public class HttpPlugin implements GwPlugin {

    private final HttpPluginServer server;

    public HttpPlugin() {
        server = new HttpPluginServer();
    }

    @Override
    public void start() {
        try {
            server.start();
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public void stop() {
        try {
            server.stop();
        } catch (InterruptedException ignored) {
        }
    }
}
