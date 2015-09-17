package br.ufs.gothings.gateway;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.gateway.block.Block;
import br.ufs.gothings.gateway.block.BlockId;
import br.ufs.gothings.gateway.block.Forwarding;
import br.ufs.gothings.gateway.exceptions.InvalidForwardingException;

/**
 * @author Wagner Macedo
 */
public class InputController implements Block {
    private final CommunicationManager manager;

    public InputController(final CommunicationManager manager) {
        this.manager = manager;
    }

    @Override
    public void receiveForwarding(final BlockId sourceId, final Forwarding fwd) throws InvalidForwardingException {
        final GwMessage message = fwd.getMessage();
        performListeners((GwRequest) message);
        manager.forward(this, BlockId.INTERCONNECTION_CONTROLLER, fwd);
    }

    private void performListeners(final GwRequest msg) {
        // TODO: Method stub
    }
}
