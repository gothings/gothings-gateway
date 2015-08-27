package br.ufs.gothings.gateway.block;

import br.ufs.gothings.core.GwMessage;

/**
 * @author Wagner Macedo
 */
public interface Block {
    void receiveForwarding(BlockId sourceId, GwMessage msg);
}
