package br.ufs.gothings.core.plugin;

import br.ufs.gothings.core.message.GwReply;

/**
 * @author Wagner Macedo
 */
public interface PluginServer extends GwPlugin {
    void handleReply(GwReply reply);

    void setUp(RequestLink requestLink);
}
