package br.ufs.gothings.core.message;

/**
 * @author Wagner Macedo
 */
public final class GwReply extends DataMessage {
    public static final GwReply EMPTY = new GwReply(GwHeaders.EMPTY, Payload.EMPTY, null).readOnly();

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
     * Set this reply as read-only
     *
     * @return this reply
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

    @Override
    public MessageType getType() {
        return MessageType.REPLY;
    }
}
