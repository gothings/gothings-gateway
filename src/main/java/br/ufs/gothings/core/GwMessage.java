package br.ufs.gothings.core;

import br.ufs.gothings.core.message.Payload;

/**
 * @author Wagner Macedo
 */
public final class GwMessage {
    private final GwHeaders headers;
    private final Payload payload;

    public GwMessage() {
        this.headers = new GwHeaders();
        this.payload = new Payload();
    }

    public GwHeaders headers() {
        return headers;
    }

    public Payload payload() {
        return payload;
    }
}
