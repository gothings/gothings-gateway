package br.ufs.gothings.core.sink;

import br.ufs.gothings.core.GwMessage;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import org.apache.commons.lang3.Validate;

import java.util.concurrent.*;

/**
 * @author Wagner Macedo
 */
public class Sink {
    private final ExecutorService executor;
    private final Disruptor<SinkEvent> disruptor;
    private final RingBuffer<SinkEvent> ringBuffer;

    private final ValueSinkLink leftLink;
    private final ValueSinkLink rightLink;
    private final ValueSinkEventHandler eventHandler;

    @SuppressWarnings("unchecked")
    public Sink() {
        leftLink = new ValueSinkLink();
        rightLink = new ValueSinkLink();
        eventHandler = new ValueSinkEventHandler();

        executor = Executors.newSingleThreadExecutor();
        disruptor = new Disruptor<>(SinkEvent::new, 1024, executor);
        disruptor.handleEventsWith(eventHandler);
        ringBuffer = disruptor.getRingBuffer();

        disruptor.start();
    }

    public SinkLink getLeftLink() {
        return leftLink;
    }

    public SinkLink getRightLink() {
        return rightLink;
    }

    private void checkStart() {
        try {
            eventHandler.waitLinks(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            // do nothing
        } catch (TimeoutException e) {
            throw new IllegalStateException("sink is not ready yet");
        }
    }

    public void stop() {
        executor.shutdown();
        disruptor.shutdown();
    }

    private static final class SinkEvent {
        private GwMessage value;
        private SinkLink sourceLink;

        GwMessage getValue() {
            return value;
        }

        void setValue(GwMessage value) {
            this.value = value;
        }

        SinkLink getSourceLink() {
            return sourceLink;
        }

        void setSourceLink(SinkLink sourceLink) {
            this.sourceLink = sourceLink;
        }
    }

    private final class ValueSinkLink implements SinkLink {
        private SinkListener listener;

        @Override
        public void send(GwMessage value) {
            Validate.notNull(value);
            checkStart();

            final long sequence = ringBuffer.next();
            final SinkEvent event = ringBuffer.get(sequence);

            event.setValue(value);
            event.setSourceLink(this);

            ringBuffer.publish(sequence);
        }

        @Override
        public void setListener(SinkListener listener) {
            Validate.validState(this.listener == null, "listener already set");
            this.listener = listener;
            eventHandler.addLink();
        }

        SinkListener getListener() {
            return listener;
        }
    }

    private final class ValueSinkEventHandler implements EventHandler<SinkEvent> {
        private final CountDownLatch latch = new CountDownLatch(2);

        void addLink() {
            Validate.validState(latch.getCount() > 0, "sink cannot have more than two links");
            latch.countDown();
        }

        void waitLinks(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
            if (!latch.await(timeout, unit)) {
                throw new TimeoutException();
            }
        }

        @Override
        public void onEvent(final SinkEvent event, long sequence, boolean endOfBatch) {
            final ValueSinkLink targetLink = (event.getSourceLink() == leftLink) ? rightLink : leftLink;
            try {
                targetLink.getListener().valueReceived(event.getValue());
            } catch (Exception ignored) {
                // Any errors are silently ignored
            }
        }
    }
}
