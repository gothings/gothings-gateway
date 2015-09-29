package br.ufs.gothings.core.message;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Wagner Macedo
 */
public abstract class GwMessage {
    public enum MessageType {
        REQUEST,
        REPLY,
        STATUS,
    }

    private volatile Long sequence;
    private final AtomicBoolean sequenceAssigned = new AtomicBoolean(false);

    public abstract MessageType getType();

    public final Long getSequence() {
        if (!sequenceAssigned.get()) {
            throw new IllegalStateException("message sequence still not set");
        }
        return sequence;
    }

    public final void setSequence(final Long sequence) {
        if (!sequenceAssigned.compareAndSet(false, true)) {
            throw new IllegalStateException("message sequence already set");
        }
        this.sequence = sequence;
    }

    public final boolean isSequenced() {
        sequenceAssigned.set(true);
        return sequence != null;
    }
}
