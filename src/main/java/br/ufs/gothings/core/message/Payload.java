package br.ufs.gothings.core.message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * @author Wagner Macedo
 */
public class Payload {
    private final ByteBuf data = Unpooled.buffer();

    public void set(byte[] bytes) {
        data.clear().writeBytes(bytes);
    }

    public void set(ByteBuffer buffer) {
        data.clear().writeBytes(buffer);
    }

    public void set(String str, Charset charset) {
        data.clear().writeBytes(str.getBytes(charset));
    }

    public byte[] asBytes() {
        return data.array();
    }

    public ByteBuffer asBuffer() {
        return data.nioBuffer();
    }

    public String asString(Charset charset) {
        return data.toString(charset);
    }
}
