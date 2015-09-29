package br.ufs.gothings.core.common;

import br.ufs.gothings.core.message.DataMessage;
import br.ufs.gothings.core.message.GwError;

/**
 * @author Wagner Macedo
 */
public final class GatewayException extends Exception {
    private final GwError error;

    public GatewayException(final GwError error) {
        this.error = error;
    }

    public GatewayException(final DataMessage message, final Reason reason) {
        this.error = new GwError(message, reason);
    }

    public GwError getErrorMessage() {
        return error;
    }
}
