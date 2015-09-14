package br.ufs.gothings.core.message;

import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.GwMessage;
import org.apache.commons.lang3.Validate;

/**
 * @author Wagner Macedo
 */
public abstract class SequencedMessage extends GwMessage {
    private Long sequence;

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
        Validate.validState(this.sequence == null, "message sequence already set");
        this.sequence = sequence;
    }
}
