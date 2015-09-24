package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.plugin.RequestLink;

/**
 * @author Wagner Macedo
 */
interface HttpPluginServer {
    void start(RequestLink requestLink, Settings settings) throws InterruptedException;
    void stop() throws InterruptedException;
}
