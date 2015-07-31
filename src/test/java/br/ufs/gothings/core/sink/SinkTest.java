package br.ufs.gothings.core.sink;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * @author Wagner Macedo
 */
public class SinkTest {
    private static ExecutorService EXECUTOR;

    @Before
    public void startExecutor() {
        EXECUTOR = Executors.newSingleThreadExecutor();
    }

    @After
    public void stopExecutor() {
        EXECUTOR.shutdown();
    }

    @Test
    public void testSinkListenerCalled() throws Exception {
        // Check if listener incremented an atomic integer and didn't change value
        Sink<AtomicInteger> sink = new Sink<>(EXECUTOR);
        sink.setListener(event -> {
            event.getValue().incrementAndGet();
        });
        AtomicInteger value = new AtomicInteger(15);
        long seq = sink.send(value);
        AtomicInteger received = sink.receive(seq);
        assertEquals(16, value.get());
        assertEquals(value, received);
        sink.stop();

        // Here it checks if listener changed the value of event
        sink = new Sink<>(EXECUTOR);
        sink.setListener(event -> {
            final int n = event.getValue().get();
            event.setValue(new AtomicInteger(n));
        });
        seq = sink.send(value);
        received = sink.receive(seq);
        assertNotEquals(value, received);
    }

    @Test
    public void testSinkListenerSameThread() throws Exception {
        // Check if receive really waits for listener call
        Sink<String> sink = new Sink<>(EXECUTOR);
        sink.setListener(event -> {
            interval(50);
        });
        final long seq = sink.send("Hello");
        try {
            sink.receive(seq, 60, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            fail("time out");
        }
    }

    @Test
    public void testSinkListenerAsynchronous() throws Exception {
        // Firstly we have a listener running in the same thread
        Sink<Integer> sink = new Sink<>(EXECUTOR);
        sink.setListener(event -> {
            interval(event.getValue());
        });
        // Checks that second message cannot be received while the first one is processing.
        // This happens because the executor is single-threaded
        sink.send(1000);
        long seq = sink.send(1);
        try {
            sink.receive(seq, 100, TimeUnit.MILLISECONDS);
            fail("it worked");
        } catch (TimeoutException ignored) {
        }
        sink.stop();

        // Now we've an asynchronous listener. First message shouldn't block the second one.
        sink = new Sink<>(EXECUTOR);
        sink.setListener(event -> {
            event.asyncJob(() -> {
                interval(event.getValue());
            });
        });
        // Checking now
        sink.send(1000);
        seq = sink.send(1);
        Integer received = null;
        try {
            received = sink.receive(seq, 100, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            fail("time out");
        }
        assertNotNull(received);
    }

    @Test(expected = IllegalStateException.class)
    public void testSinkWithoutListener() {
        Sink<String> sink = new Sink<>(EXECUTOR);
        sink.send("AAA");
    }

    private static void interval(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }
}
