package br.ufs.gothings.core.common;

import br.ufs.gothings.core.message.GwError;
import br.ufs.gothings.core.message.GwRequest;

/**
 * @author Wagner Macedo
 */
public final class GatewayException extends Exception {
    private final GwError error;

    public GatewayException(final GwError error) {
        this.error = error;
    }

    public GatewayException(final GwRequest message, final ErrorCode code) {
        this.error = new GwError(message, code);
    }

    public GwError getErrorMessage() {
        return error;
    }
}
