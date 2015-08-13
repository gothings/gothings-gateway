package br.ufs.gothings.core.sink;

import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * @author Wagner Macedo
 */
public class SinkTest {
    @Test
    public void testSinkListenerCalled() throws Exception {
        // Check if listener incremented an atomic integer and didn't change value
        Sink<AtomicInteger> sink = new Sink<>();
        sink.setHandler(event -> {
            final AtomicInteger value = event.pull();
            value.incrementAndGet();
            event.pushAndSignal(value);
        });
        AtomicInteger value = new AtomicInteger(15);
        long seq = sink.send(value);
        AtomicInteger received = sink.receive(seq, 1, TimeUnit.MINUTES);
        assertEquals(16, value.get());
        assertEquals(value, received);
        sink.stop();

        // Here it checks if listener changed the value of event
        sink = new Sink<>();
        sink.setHandler(event -> {
            final int n = event.pull().get();
            event.pushAndSignal(new AtomicInteger(n));
        });
        seq = sink.send(value);
        received = sink.receive(seq, 1, TimeUnit.MINUTES);
        assertNotEquals(value, received);
    }

    @Test
    public void testSinkListenerSameThread() throws Exception {
        // Check if receive really waits for listener call
        Sink<String> sink = new Sink<>();
        sink.setHandler(event -> {
            interval(50);
            event.pushAndSignal(null);
        });
        final long seq = sink.send("Hello");
        try {
            sink.receive(seq, 60, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            fail("time out");
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testSinkWithoutListener() {
        Sink<String> sink = new Sink<>();
        sink.send("AAA");
    }

    private static void interval(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }
}
