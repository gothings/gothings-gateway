package br.ufs.gothings.core.message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ReadOnlyByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Math.min;

/**
 * @author Wagner Macedo
 */
public class Payload {
    public static final Payload EMPTY = new Payload().readOnly();

    private final AtomicReference<ByteBuf> data = new AtomicReference<>();

    public Payload() {
        this.data.set(Unpooled.buffer());
    }

    public void set(byte[] bytes) {
        data.get().clear().writeBytes(bytes);
    }

    public void set(final InputStream in) throws IOException {
        set(in, true);
    }

    public void set(final InputStream in, boolean check) throws IOException {
        data.get().clear();
        final byte[] bytes = new byte[1024];

        while (true) {
            final int len = check ? min(in.available(), 1024) : 1024;
            final int read = in.read(bytes, 0, len);
            if (read > 0) {
                data.get().writeBytes(bytes, 0, read);
            } else {
                break;
            }
        }
    }

    public void set(ByteBuffer buffer) {
        data.get().clear().writeBytes(buffer);
    }

    public void set(String str, Charset charset) {
        data.get().clear().writeBytes(str.getBytes(charset));
    }

    public byte[] asBytes() {
        final ByteBuf data = this.data.get();
        if (data instanceof ReadOnlyByteBuf) {
            return data.copy().array();
        }
        return data.array();
    }

    public InputStream asInputStream() {
        return new ByteBufInputStream(data.get().duplicate());
    }

    public ByteBuffer asBuffer() {
        return data.get().copy().nioBuffer();
    }

    public String asString(Charset charset) {
        return data.get().toString(charset);
    }

    public Payload readOnly() {
        data.updateAndGet(bb -> !(bb instanceof ReadOnlyByteBuf) ? Unpooled.unmodifiableBuffer(bb) : bb);
        return this;
    }
}
