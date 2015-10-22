package br.ufs.gothings.core.message;

import br.ufs.gothings.core.message.headers.GwHeaders;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Wagner Macedo
 */
public abstract class GwMessage {
    public enum MessageType {
        REQUEST,
        REPLY,
        ERROR,
    }

    private volatile long sequence;
    private final AtomicBoolean sequenceAssigned = new AtomicBoolean(false);
    private final GwHeaders headers;

    protected GwMessage(GwHeaders headers) {
        this.headers = (headers != null) ? headers : new GwHeaders();
    }

    protected GwMessage(GwHeaders headers, long sequence) {
        this(headers);
        setSequence(sequence);
    }

    public final GwHeaders headers() {
        return headers;
    }

    public final long getSequence() {
        if (!sequenceAssigned.get()) {
            throw new IllegalStateException("message sequence still not set");
        }
        return sequence;
    }

    public final void setSequence(final long sequence) {
        if (!sequenceAssigned.compareAndSet(false, true)) {
            throw new IllegalStateException("message sequence already set");
        }
        this.sequence = sequence;
    }

    protected GwMessage readOnly() {
        sequenceAssigned.set(true);
        headers.readOnly();
        return this;
    }

    public abstract MessageType getType();
}
