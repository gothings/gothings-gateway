package br.ufs.gothings.core.message;

/**
 * @author Wagner Macedo
 */
public final class GwReply extends SequencedMessage {
    { type = MessageType.REPLY; }

    public GwReply(final Long sequence) {
        super(sequence);
    }

    public GwReply(GwRequest req) {
        super(req.headers(), req.payload(), req.getSequence());
    }
}
