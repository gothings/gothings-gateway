package br.ufs.gothings.gateway;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.gateway.block.BlockId;
import br.ufs.gothings.gateway.block.Block;

/**
 * @author Wagner Macedo
 */
public class InterconnectionController implements Block {
    private final CommunicationManager manager;

    public InterconnectionController(final CommunicationManager manager) {

        this.manager = manager;
    }

    @Override
    public void receiveForwarding(final BlockId sourceId, final GwMessage msg) {
        switch (sourceId) {
            case INPUT_CONTROLLER:
//                if () {
                manager.forward(this, BlockId.OUTPUT_CONTROLLER, msg);
//                } else {
                manager.forward(this, BlockId.COMMUNICATION_MANAGER, msg);
//                }
                break;
            case COMMUNICATION_MANAGER:
                manager.forward(this, BlockId.OUTPUT_CONTROLLER, msg);
                break;
        }
    }
}
