package br.ufs.gothings.core.message;

/**
 * @author Wagner Macedo
 */
public abstract class DataMessage extends GwMessage {

    private final Payload payload;

    protected DataMessage() {
        this(null, null);
    }

    protected DataMessage(GwHeaders headers, Payload payload) {
        super(headers);
        this.payload = (payload != null) ? payload : new Payload();
    }

    protected DataMessage(final GwHeaders headers, final Payload payload, final Long sequence) {
        this(headers, payload);
        setSequence(sequence);
    }

    public final Payload payload() {
        return payload;
    }
}
