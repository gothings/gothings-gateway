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
    @Override
    public GwRequest readOnly() {
        super.readOnly();
        return this;
    }

    @Override
    public MessageType getType() {
        return MessageType.REQUEST;
    }
}
