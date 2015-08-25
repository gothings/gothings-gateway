package br.ufs.gothings.core.sink;

import java.util.concurrent.*;

/**
 * @author Wagner Macedo
 */
public final class SinkEvent<T> {
    private final CountDownLatch writeLatch = new CountDownLatch(1);
    private SinkLink<T> sourceLink;
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

    SinkLink<T> getSourceLink() {
        return sourceLink;
    }

    void setSourceLink(SinkLink<T> sourceLink) {
        this.sourceLink = sourceLink;
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
