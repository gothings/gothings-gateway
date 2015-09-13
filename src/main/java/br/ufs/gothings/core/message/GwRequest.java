package br.ufs.gothings.core.message;

/**
 * @author Wagner Macedo
 */
public final class GwRequest extends SequencedMessage {
    { type = MessageType.REQUEST; }

    public GwRequest() {
        super(null);
    }
}
