package br.ufs.gothings.core;

import br.ufs.gothings.core.message.MessageType;
import org.apache.commons.lang3.Validate;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Wagner Macedo
 */
public abstract class GwMessage {
    protected MessageType type;

    private final AtomicReference<Long> sequence = new AtomicReference<>();
    private final AtomicBoolean sequenceLock = new AtomicBoolean(false);

    public final MessageType getType() {
        return type;
    }

    public final Long getSequence() {
        return sequence.get();
    }

    public final void setSequence(final long sequence) {
        Validate.validState(sequenceLock.get() || (this.sequence.get() == null),
                "message sequence locked for set or already set");
        this.lockSequence();
        this.sequence.set(sequence);
    }

    public final void lockSequence() {
        sequenceLock.set(true);
    }

    public final boolean isSequenced() {
        lockSequence();
        return getSequence() != null;
    }
}
