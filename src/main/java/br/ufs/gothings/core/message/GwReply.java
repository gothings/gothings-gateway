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
        lockSequence();
    }

    public GwReply(final GwHeaders headers, final Payload payload, final Long sequence) {
        super(headers, payload, sequence);
    }

    /**
     * Set this reply as read-only and construct a view with a new sequence
     *
     * @param sequence  new sequence
     * @return a read-only reply
     */
    public GwReply readOnly(final Long sequence) {
        final GwReply reply = new GwReply(this.headers().readOnly(), this.payload().readOnly(), sequence);
        reply.lockSequence();
        return reply;
    }

    @Override
    public MessageType getType() {
        return MessageType.REPLY;
    }

    private static final Payload emptyPayload = new Payload().readOnly();

    public static GwReply emptyReply(GwRequest req) {
        return new GwReply(req.headers().readOnly(), emptyPayload, req.getSequence());
    }
}
