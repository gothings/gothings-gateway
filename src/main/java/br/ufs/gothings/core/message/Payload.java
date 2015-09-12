package br.ufs.gothings.core.message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static java.lang.Math.min;

/**
 * @author Wagner Macedo
 */
public class Payload {
    private final ByteBuf data = Unpooled.buffer();

    public void set(byte[] bytes) {
        data.clear().writeBytes(bytes);
    }

    public void set(final InputStream in) throws IOException {
        set(in, true);
    }

    public void set(final InputStream in, boolean check) throws IOException {
        data.clear();
        final byte[] bytes = new byte[1024];

        while (true) {
            final int len = check ? min(in.available(), 1024) : 1024;
            final int read = in.read(bytes, 0, len);
            if (read > 0) {
                data.writeBytes(bytes, 0, read);
            } else {
                break;
            }
        }
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

    public InputStream asInputStream() {
        return new ByteBufInputStream(data);
    }

    public ByteBuffer asBuffer() {
        return data.nioBuffer();
    }

    public String asString(Charset charset) {
        return data.toString(charset);
    }
}
