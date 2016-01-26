package br.ufs.gothings.core.message;

import br.ufs.gothings.core.common.ErrorCode;

/**
 * @author Wagner Macedo
 */
public class GwError extends GwMessage {
    private final ErrorCode code;

    public GwError(final GwRequest message, final ErrorCode code) {
        super(message.headers().readOnly(), message.getSequence());
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    @Override
    public MessageType getType() {
        return MessageType.ERROR;
    }
}
