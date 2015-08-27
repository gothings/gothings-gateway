package br.ufs.gothings.core;

import br.ufs.gothings.core.sink.Sink;
import br.ufs.gothings.core.sink.SinkLink;

/**
 * @author Wagner Macedo
 */
public interface GwPlugin {
    void start();

    void stop();

    SinkLink<GwMessage> clientLink();

    SinkLink<GwMessage> serverLink();

    String getProtocol();

    Settings settings();
}
