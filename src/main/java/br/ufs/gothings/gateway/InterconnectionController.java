package br.ufs.gothings.gateway;

import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.gateway.block.Block;
import br.ufs.gothings.gateway.block.BlockId;
import br.ufs.gothings.gateway.block.Forwarding;
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
    public void receiveForwarding(final BlockId sourceId, final Forwarding fwd) throws InvalidForwardingException {
        switch (sourceId) {
            case INPUT_CONTROLLER:
                final GwRequest request = (GwRequest) fwd.getMessage();
                final GwReply cached = getCache(request);
                if (cached != null) {
                    manager.forward(this, BlockId.OUTPUT_CONTROLLER, new Forwarding(cached, null));
                } else {
                    manager.forward(this, BlockId.COMMUNICATION_MANAGER, new Forwarding(request, null));
                }
                break;
            case COMMUNICATION_MANAGER:
                final GwReply reply = (GwReply) fwd.getMessage();
                setCache(reply);
                manager.forward(this, BlockId.OUTPUT_CONTROLLER, new Forwarding(reply, null));
                break;
        }
    }

    private void setCache(final GwReply reply) {

    }

    private GwReply getCache(final GwRequest req) {
        if (req.headers().get(GwHeaders.OPERATION) != Operation.READ) {
            return null;
        }

        // TODO: Method stub
        return null;
    }
}
