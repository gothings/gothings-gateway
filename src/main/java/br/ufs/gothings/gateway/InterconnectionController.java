package br.ufs.gothings.gateway;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.gateway.block.BlockId;
import br.ufs.gothings.gateway.block.Block;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.gateway.exceptions.InvalidForwardingException;

/**
 * @author Wagner Macedo
 */
public class InterconnectionController implements Block {
    private final CommunicationManager manager;

    public InterconnectionController(final CommunicationManager manager) {

        this.manager = manager;
    }

    @Override
    public void receiveForwarding(final BlockId sourceId, final GwMessage msg) throws InvalidForwardingException {
        switch (sourceId) {
            case INPUT_CONTROLLER:
                final GwMessage cached;
                if (msg.headers().operationHeader().get() == Operation.READ && (cached = getCachedMessage(msg)) != null) {
                    manager.forward(this, BlockId.OUTPUT_CONTROLLER, cached);
                } else {
                    manager.forward(this, BlockId.COMMUNICATION_MANAGER, msg);
                }
                break;
            case COMMUNICATION_MANAGER:
                manager.forward(this, BlockId.OUTPUT_CONTROLLER, msg);
                break;
        }
    }

    private GwMessage getCachedMessage(final GwMessage msg) {
        // TODO: Method stub
        return null;
    }
}
