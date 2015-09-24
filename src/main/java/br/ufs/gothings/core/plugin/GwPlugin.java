package br.ufs.gothings.core.plugin;

import br.ufs.gothings.core.Settings;
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
