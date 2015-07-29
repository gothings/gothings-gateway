package br.ufs.gothings.core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.ByteBuffer;

/**
 * @author Wagner Macedo
 */
public final class GwMessage {
    private final GwHeaders headers;
    private final ByteBuf payload;

    public GwMessage() {
        this.headers = new GwHeaders();
        this.payload = Unpooled.buffer();
    }

    public void setPayload(ByteBuf payload) {
        this.payload.clear().writeBytes(payload);
    }

    public void setPayload(String payload) {
        this.payload.clear().writeBytes(payload.getBytes());
    }

    public ByteBuf payload() {
        return payload;
    }

    public GwHeaders headers() {
        return headers;
    }
}
