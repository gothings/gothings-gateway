package br.ufs.gothings.core.message;

import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.GwMessage;
import org.apache.commons.lang3.Validate;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Wagner Macedo
 */
public abstract class SequencedMessage extends GwMessage {
    private Long sequence;
    protected final AtomicBoolean allowSequence = new AtomicBoolean(true);

    protected SequencedMessage(final GwHeaders headers, final Payload payload, final Long sequence) {
        super(headers, payload);
        this.sequence = sequence;
    }

    protected SequencedMessage(Long sequence) {
        this(null, null, sequence);
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
