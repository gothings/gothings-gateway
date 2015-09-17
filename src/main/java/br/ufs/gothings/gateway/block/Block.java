package br.ufs.gothings.gateway.block;

/**
 * @author Wagner Macedo
 */
public interface Block {
    void receiveForwarding(BlockId sourceId, Forwarding fwd) throws Exception;
}
