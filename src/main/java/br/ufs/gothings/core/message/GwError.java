package br.ufs.gothings.core.message;

import br.ufs.gothings.core.common.ErrorCode;

/**
 * @author Wagner Macedo
 */
public class GwError extends GwMessage {
    private final ErrorCode code;
    private final MessageType sourceType;

    public GwError(final GwRequest message, final ErrorCode code) {
        super(message.headers().readOnly(), message.getSequence());
        this.code = code;
        this.sourceType = message.getType();
    }

    public ErrorCode getCode() {
        return code;
    }

    public MessageType getSourceType() {
        return sourceType;
    }

    @Override
    public MessageType getType() {
        return MessageType.ERROR;
    }
}
