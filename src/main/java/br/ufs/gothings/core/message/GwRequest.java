package br.ufs.gothings.core.message;

/**
 * @author Wagner Macedo
 */
public final class GwRequest extends DataMessage {
    public GwRequest() {
    }

    public GwRequest(final GwHeaders headers, final Payload payload) {
        super(headers, payload);
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
