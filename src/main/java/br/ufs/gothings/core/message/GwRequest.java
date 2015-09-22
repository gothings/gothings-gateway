package br.ufs.gothings.core.message;

/**
 * @author Wagner Macedo
 */
public final class GwRequest extends DataMessage {
    @Override
    public MessageType getType() {
        return MessageType.REQUEST;
    }
}
