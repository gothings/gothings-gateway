package br.ufs.gothings.gateway;

import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.gateway.block.Block;
import br.ufs.gothings.gateway.block.Package;

/**
 * @author Wagner Macedo
 */
public class OutputController implements Block {

    @Override
    public void process(final Package pkg) throws Exception {
        performListeners((GwReply) pkg.getMessage());
    }

    private void performListeners(final GwReply msg) {
        // TODO: Method stub
    }
}
