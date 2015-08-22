package br.ufs.gothings.core.sink;

import java.util.concurrent.*;

/**
 * @author Wagner Macedo
 */
public final class SinkEvent<T> {
    private final CountDownLatch writeLatch = new CountDownLatch(1);
    private SinkLink<T> link;
    private T value;

    public T readValue() {
        return value;
    }

    public void writeValue(T value) {
        this.value = value;
        finish();
    }

    public void finish() {
        writeLatch.countDown();
    }

    /* Internal use */

    SinkLink<T> getLink() {
        return link;
    }

    void setLink(SinkLink<T> link) {
        this.link = link;
    }

    T getValue() {
        return value;
    }

    void setValue(T value) {
        this.value = value;
    }

    void waitFinish(int timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        if (!writeLatch.await(timeout, unit)) {
            throw new TimeoutException();
        }
    }
}
