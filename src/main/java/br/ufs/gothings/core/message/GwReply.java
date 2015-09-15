package br.ufs.gothings.core.message;

/**
 * @author Wagner Macedo
 */
public final class GwReply extends DataMessage {
    { type = MessageType.REPLY; }

    public GwReply(GwRequest req) {
        super(req.headers(), req.payload(), req.getSequence());
    }
}
