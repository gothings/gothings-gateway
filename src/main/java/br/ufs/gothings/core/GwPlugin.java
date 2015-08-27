package br.ufs.gothings.core;

import br.ufs.gothings.core.sink.Sink;

/**
 * @author Wagner Macedo
 */
public interface GwPlugin {
    void start();

    void stop();

    Sink<GwMessage> clientSink();

    Sink<GwMessage> serverSink();

    String getProtocol();

    Settings settings();
}
