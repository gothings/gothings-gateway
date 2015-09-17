package br.ufs.gothings.gateway.block;

/**
 * @author Wagner Macedo
 */
public interface Block {
    void receiveForwarding(BlockId sourceId, Package pkg) throws Exception;
}
