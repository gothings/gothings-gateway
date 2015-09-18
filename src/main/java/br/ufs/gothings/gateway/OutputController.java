package br.ufs.gothings.gateway;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.gateway.block.Block;
import br.ufs.gothings.gateway.block.BlockId;
import br.ufs.gothings.gateway.block.Package;

/**
 * @author Wagner Macedo
 */
public class OutputController implements Block {
    private final CommunicationManager manager;

    public OutputController(final CommunicationManager manager) {
        this.manager = manager;
    }

    @Override
    public void receiveForwarding(final BlockId sourceId, final Package pkg) {
        final GwMessage message = pkg.getInfo().getMessage();
        performListeners((GwReply) message);
        manager.forward(this, BlockId.COMMUNICATION_MANAGER, pkg);
    }

    private void performListeners(final GwReply msg) {
        // TODO: Method stub
    }
}
