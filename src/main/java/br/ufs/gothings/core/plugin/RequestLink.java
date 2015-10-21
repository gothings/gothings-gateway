package br.ufs.gothings.core.plugin;

import br.ufs.gothings.core.message.GwRequest;

/**
 * @author Wagner Macedo
 */
public interface RequestLink {
    FutureReply send(GwRequest request);
}
