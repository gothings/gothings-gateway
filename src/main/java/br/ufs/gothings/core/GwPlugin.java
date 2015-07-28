package br.ufs.gothings.core;

/**
 * @author Wagner Macedo
 */
public interface GwPlugin {
    void start(CommunicationManager manager);

    void stop();

    PluginSettings settings();
}
