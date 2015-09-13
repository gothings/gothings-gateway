package br.ufs.gothings.core.message;

import br.ufs.gothings.core.GwMessage;

/**
 * @author Wagner Macedo
 */
public final class GwReply extends GwMessage {
    public GwReply() {
        super(null);
        allowSequence.set(false);
    }

    public GwReply(final Long sequence) {
        super(sequence);
    }

    public GwReply(GwRequest req) {
        super(req.headers(), req.payload(), req.getSequence());
    }

    @Override
    public boolean isReply() {
        return true;
    }
}
