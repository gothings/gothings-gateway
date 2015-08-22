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
        // Check if handler incremented an atomic integer and didn't change value
        Sink<AtomicInteger> sink = new Sink<>();
        sink.createLink(event -> {
            final AtomicInteger value = event.readValue();
            value.incrementAndGet();
            event.finish();
        });
        SinkLink<AtomicInteger> sinkLink = sink.createLink(null);
        AtomicInteger value = new AtomicInteger(15);
        long seq = sinkLink.put(value);
        AtomicInteger received = sinkLink.get(seq, 1, TimeUnit.MINUTES);
        assertEquals(16, value.get());
        assertEquals(value, received);
        sink.stop();

        // Here it checks if handler changed the value of event
        sink = new Sink<>();
        sink.createLink(event -> {
            final int n = event.readValue().get();
            event.writeValue(new AtomicInteger(n));
        });
        sinkLink = sink.createLink(null);
        seq = sinkLink.put(value);
        received = sinkLink.get(seq, 1, TimeUnit.MINUTES);
        assertNotEquals(value, received);
    }

    @Test
    public void testSinkListenerSameThread() throws Exception {
        // Check if 'get' really waits for listener call
        final Sink<String> sink = new Sink<>();
        sink.createLink(event -> {
            interval(50);
            event.finish();
        });
        final SinkLink<String> sinkLink = sink.createLink(null);
        final long seq = sinkLink.put("Hello");
        try {
            sinkLink.get(seq, 60, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            fail("time out");
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testSinkHavingOnlyOneLink() {
        final Sink<String> sink = new Sink<>();
        final SinkLink<String> sinkLink = sink.createLink(null);
        sinkLink.put("AAA");
    }

    private static void interval(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }
}
