package br.ufs.gothings.gateway;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.gateway.block.BlockId;
import br.ufs.gothings.gateway.block.Block;
import br.ufs.gothings.gateway.exceptions.InvalidForwardingException;

/**
 * @author Wagner Macedo
 */
public class OutputController implements Block {
    private final CommunicationManager manager;

    public OutputController(final CommunicationManager manager) {
        this.manager = manager;
    }

    @Override
    public void receiveForwarding(final BlockId sourceId, final GwMessage msg) throws InvalidForwardingException {
        performListeners(msg);
        manager.forward(this, BlockId.COMMUNICATION_MANAGER, msg);
    }

    private void performListeners(final GwMessage msg) {
        // TODO: Method stub
    }
}
