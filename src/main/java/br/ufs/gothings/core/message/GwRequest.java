package br.ufs.gothings.core.message;

/**
 * @author Wagner Macedo
 */
public final class GwRequest extends DataMessage {
    public GwRequest() {
    }

    private GwRequest(final GwHeaders headers, final Payload payload, final long sequence) {
        super(headers, payload, sequence);
    }

    /**
     * Construct a read-only view of this request
     *
     * @return a read-only request
     */
    public GwRequest readOnly() {
        return new GwRequest(headers().readOnly(), payload().readOnly(), getSequence());
    }

    @Override
    public MessageType getType() {
        return MessageType.REQUEST;
    }
}
