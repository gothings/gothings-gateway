package br.ufs.gothings.core.message;

/**
 * @author Wagner Macedo
 */
public final class GwReply extends DataMessage {
    { type = MessageType.REPLY; }

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
}
