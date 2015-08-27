package br.ufs.gothings.gateway;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.gateway.block.BlockId;
import br.ufs.gothings.gateway.block.Block;

/**
 * @author Wagner Macedo
 */
public class InputController implements Block {
    private final CommunicationManager manager;

    public InputController(final CommunicationManager manager) {
        this.manager = manager;
    }

    @Override
    public void receiveForwarding(final BlockId sourceId, final GwMessage msg) {
        manager.forward(this, BlockId.INTERCONNECTION_CONTROLLER, msg);
    }
}
