package br.ufs.gothings.core.sink;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Wagner Macedo
 */
public interface SinkLink<T> {
    long put(T value);

    T get(long sequence, int timeout, TimeUnit unit) throws InterruptedException, TimeoutException;
}
