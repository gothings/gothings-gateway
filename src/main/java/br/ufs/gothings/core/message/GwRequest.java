package br.ufs.gothings.core.message;

import br.ufs.gothings.core.GwMessage;

/**
 * @author Wagner Macedo
 */
public final class GwRequest extends GwMessage {
    @Override
    public boolean isReply() {
        return false;
    }
}
