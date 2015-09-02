package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.sink.SinkLink;

/**
 * @author Wagner Macedo
 */
interface HttpPluginServer {
    void start(SinkLink<GwMessage> sinkLink, Settings settings) throws InterruptedException;
    void stop() throws InterruptedException;
}
