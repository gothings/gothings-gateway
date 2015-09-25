package br.ufs.gothings.core.plugin;

import br.ufs.gothings.core.Settings;

/**
 * @author Wagner Macedo
 */
public interface GwPlugin {
    void start();

    void stop();

    String getProtocol();

    Settings settings();
}
