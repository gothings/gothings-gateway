package br.ufs.gothings.gateway;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.gateway.block.Block;
import br.ufs.gothings.gateway.block.BlockId;
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
                final GwRequest request = (GwRequest) msg;
                final GwReply cached = getCache(request);
                if (cached != null) {
                    manager.forward(this, BlockId.OUTPUT_CONTROLLER, cached);
                } else {
                    manager.forward(this, BlockId.COMMUNICATION_MANAGER, request);
                }
                break;
            case COMMUNICATION_MANAGER:
                final GwReply reply = (GwReply) msg;
                setCache(reply);
                manager.forward(this, BlockId.OUTPUT_CONTROLLER, reply);
                break;
        }
    }

    private void setCache(final GwReply reply) {

    }

    private GwReply getCache(final GwRequest req) {
        if (req.headers().operationHeader().get() != Operation.READ) {
            return null;
        }

        // TODO: Method stub
        return null;
    }
}
