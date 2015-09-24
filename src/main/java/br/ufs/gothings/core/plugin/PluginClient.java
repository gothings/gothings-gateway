package br.ufs.gothings.core.plugin;

import br.ufs.gothings.core.message.GwRequest;

/**
 * @author Wagner Macedo
 */
public interface PluginClient extends GwPlugin {
    void handleRequest(GwRequest request);

    void setUp(ReplyLink replyLink);
}
