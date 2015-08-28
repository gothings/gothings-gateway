package br.ufs.gothings.core.sink;

import org.junit.Test;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * @author Wagner Macedo
 */
public class SinkTest {
    private static final SynchronousQueue<Object> pipe = new SynchronousQueue<>();

    @Test
    public void testSinkListenerCalled() throws InterruptedException {
        // Check if listener incremented an atomic integer and didn't change value
        Sink<AtomicInteger> sink = new Sink<>();
        final SinkLink<AtomicInteger> firstLink = sink.getLeftLink();
        firstLink.setListener(value -> {
            value.incrementAndGet();
            firstLink.send(value);
        });

        final SinkLink<AtomicInteger> secondLink = sink.getRightLink();
        secondLink.setListener(pipe::put);
        AtomicInteger value = new AtomicInteger(15);
        secondLink.send(value);

        final AtomicInteger received = (AtomicInteger) pipe.take();
        assertEquals(16, value.get());
        assertSame(value, received);
        sink.stop();
    }

    @Test
    public void testSinkLinkDuplex() throws InterruptedException {
        // Check if links really have a duplex channel among them
        final Sink<String> sink = new Sink<>();
        final SinkLink<String> firstLink = sink.getLeftLink();
        firstLink.setListener(value -> firstLink.send(value + "World"));

        final SinkLink<String> secondLink = sink.getRightLink();
        secondLink.setListener(pipe::put);
        secondLink.send("Hello");

        final String received = (String) pipe.take();
        assertEquals("HelloWorld", received);
    }

    @Test(expected = IllegalStateException.class)
    public void testSinkNotReadyYet() {
        final Sink<String> sink = new Sink<>();
        final SinkLink<String> sinkLink = sink.getLeftLink();
        sinkLink.send("AAA");
    }
}
