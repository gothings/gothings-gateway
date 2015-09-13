package br.ufs.gothings.core.message;

import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.GwMessage;

/**
 * @author Wagner Macedo
 */
public abstract class UnsequencedMessage extends GwMessage {
    protected UnsequencedMessage(final GwHeaders headers, final Payload payload) {
        super(headers, payload);
    }
}
