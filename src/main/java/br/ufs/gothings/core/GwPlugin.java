package br.ufs.gothings.core;

import br.ufs.gothings.core.message.sink.MessageLink;

/**
 * @author Wagner Macedo
 */
public interface GwPlugin {
    void start();

    void stop();

    MessageLink clientLink();

    MessageLink serverLink();

    String getProtocol();

    Settings settings();
}
