package br.ufs.gothings.core;

import br.ufs.gothings.core.message.MessageType;
import org.apache.commons.lang3.Validate;

/**
 * @author Wagner Macedo
 */
public abstract class GwMessage {
    protected MessageType type;
    protected Long sequence;

    public final MessageType getType() {
        return type;
    }

    public final Long getSequence() {
        return sequence;
    }

    public final void setSequence(final long sequence) {
        Validate.validState(this.sequence == null, "message sequence already set");
        this.sequence = sequence;
    }
}
