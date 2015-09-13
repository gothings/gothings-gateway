package br.ufs.gothings.core;

import br.ufs.gothings.core.message.Payload;
import org.apache.commons.lang3.Validate;

/**
 * @author Wagner Macedo
 */
public abstract class GwMessage {
    private final GwHeaders headers;
    private final Payload payload;

    private Long sequence;

    protected GwMessage(GwHeaders headers, Payload payload, Long sequence) {
        this.headers = headers;
        this.payload = payload;
        this.sequence = sequence;
    }

    protected GwMessage(Long sequence) {
        this(new GwHeaders(), new Payload(), sequence);
    }

    protected GwMessage() {
        this(null);
    }

    public final GwHeaders headers() {
        return headers;
    }

    public final Payload payload() {
        return payload;
    }

    public final Long getSequence() {
        return sequence;
    }

    public final void setSequence(final long sequence) {
        Validate.validState(this.sequence == null, "message sequence already set");
        this.sequence = sequence;
    }

    public abstract boolean isReply();
}
