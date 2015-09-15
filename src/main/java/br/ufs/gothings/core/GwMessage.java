package br.ufs.gothings.core;

import br.ufs.gothings.core.message.MessageType;

/**
 * @author Wagner Macedo
 */
public abstract class GwMessage {
    protected MessageType type;

    public final MessageType getType() {
        return type;
    }
}
