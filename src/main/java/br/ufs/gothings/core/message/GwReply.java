package br.ufs.gothings.core.message;

/**
 * @author Wagner Macedo
 */
public final class GwReply extends DataMessage {
    /**
     * Construct a reply to a specific request.
     *
     * @param req    request to be replied
     */
    public GwReply(GwRequest req) {
        super(req.headers(), req.payload(), req.getSequence());
    }

    /**
     * Construct an unsequenced reply, i.e. an independent reply, used to send
     * data without match a previous request.
     */
    public GwReply() {
        super();
        setSequence(null);
    }

    public GwReply(final GwHeaders headers, final Payload payload, final Long sequence) {
        super(headers, payload, sequence);
    }

    /**
     * Construct a read-only view of this reply
     *
     * @return a read-only reply
     */
    public GwReply readOnly() {
        return readOnly(getSequence());
    }

    /**
     * Set this reply as read-only and construct a view with a new sequence
     *
     * @param sequence  new sequence
     * @return a read-only reply
     */
    public GwReply readOnly(final Long sequence) {
        return new GwReply(this.headers().readOnly(), this.payload().readOnly(), sequence);
    }

    public static GwReply readOnly(GwHeaders headers, final Payload payload, final Long sequence) {
        return new GwReply(headers.readOnly(), payload, sequence);
    }

    @Override
    public MessageType getType() {
        return MessageType.REPLY;
    }
}
