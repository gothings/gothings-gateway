package br.ufs.gothings.core.message;

/**
 * @author Wagner Macedo
 */
public final class GwReply extends DataMessage {
    public static final GwReply EMPTY = new GwReply(GwHeaders.EMPTY, Payload.EMPTY, 0).readOnly();

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
        setSequence(0);
    }

    public GwReply(final GwHeaders headers, final Payload payload, final long sequence) {
        super(headers, payload, sequence);
    }

    /**
     * Set this reply as read-only
     *
     * @return this reply
     */
    @Override
    public GwReply readOnly() {
        super.readOnly();
        return this;
    }

    /**
     * Construct a view of this reply with a new sequence
     *
     * @param sequence  new sequence
     * @return a reply
     */
    public GwReply withSequence(final long sequence) {
        return new GwReply(this.headers(), this.payload(), sequence);
    }

    @Override
    public MessageType getType() {
        return MessageType.REPLY;
    }
}
