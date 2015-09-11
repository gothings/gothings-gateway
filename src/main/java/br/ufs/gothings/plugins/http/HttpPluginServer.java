package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.message.sink.MessageLink;

/**
 * @author Wagner Macedo
 */
interface HttpPluginServer {
    void start(MessageLink messageLink, Settings settings) throws InterruptedException;
    void stop() throws InterruptedException;
}
