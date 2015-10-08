package br.ufs.gothings.gateway;

import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.gateway.block.Block;
import br.ufs.gothings.gateway.block.Package;

/**
 * @author Wagner Macedo
 */
public class InputController implements Block {

    @Override
    public void process(final Package pkg) throws Exception {
        performListeners((GwRequest) pkg.getMessage());
    }

    private void performListeners(final GwRequest msg) {
        // TODO: Method stub
    }
}
