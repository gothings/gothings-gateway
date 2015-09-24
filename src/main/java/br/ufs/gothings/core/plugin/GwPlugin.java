package br.ufs.gothings.core.plugin;

import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.message.sink.MessageLink;

/**
 * @author Wagner Macedo
 */
public interface GwPlugin {
    void start();

    void stop();

    @Deprecated
    MessageLink clientLink();

    @Deprecated
    MessageLink serverLink();

    String getProtocol();

    Settings settings();
}
