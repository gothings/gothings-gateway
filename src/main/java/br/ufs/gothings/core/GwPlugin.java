package br.ufs.gothings.core;

import br.ufs.gothings.core.sink.SinkLink;

/**
 * @author Wagner Macedo
 */
public interface GwPlugin {
    void start();

    void stop();

    SinkLink clientLink();

    SinkLink serverLink();

    String getProtocol();

    Settings settings();
}
