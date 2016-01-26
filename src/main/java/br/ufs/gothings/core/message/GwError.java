package br.ufs.gothings.core.message;

import br.ufs.gothings.core.common.Reason;

/**
 * @author Wagner Macedo
 */
public class GwError extends GwMessage {
    private final Reason reason;
    private final MessageType sourceType;

    public GwError(final GwRequest message, final Reason reason) {
        super(message.headers().readOnly(), message.getSequence());
        this.reason = reason;
        this.sourceType = message.getType();
    }

    public Reason getReason() {
        return reason;
    }

    public MessageType getSourceType() {
        return sourceType;
    }

    @Override
    public MessageType getType() {
        return MessageType.ERROR;
    }
}
