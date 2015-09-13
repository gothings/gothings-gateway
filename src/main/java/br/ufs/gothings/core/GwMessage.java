package br.ufs.gothings.core;

import br.ufs.gothings.core.message.MessageType;
import br.ufs.gothings.core.message.Payload;
import org.apache.commons.lang3.Validate;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Wagner Macedo
 */
public abstract class GwMessage {
    private final GwHeaders headers;
    private final Payload payload;

    protected MessageType type;
    private Long sequence;
    protected final AtomicBoolean allowSequence = new AtomicBoolean(true);

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

    public final MessageType getType() {
        return type;
    }

    public final Long getSequence() {
        return sequence;
    }

    public final void setSequence(final long sequence) {
        Validate.validState(this.sequence == null || !allowSequence.get(),
                "message sequence already set or not allowed to set");
        this.sequence = sequence;
    }
}
