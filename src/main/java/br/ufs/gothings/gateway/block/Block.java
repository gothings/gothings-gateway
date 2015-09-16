package br.ufs.gothings.gateway.block;

import br.ufs.gothings.gateway.exceptions.InvalidForwardingException;

/**
 * @author Wagner Macedo
 */
public interface Block {
    void receiveForwarding(BlockId sourceId, Forwarding fwd) throws InvalidForwardingException;
}
