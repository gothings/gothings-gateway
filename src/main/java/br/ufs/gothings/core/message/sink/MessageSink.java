package br.ufs.gothings.core.message.sink;

import br.ufs.gothings.core.message.DataMessage;
import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.message.GwNews;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Wagner Macedo
 */
public class MessageSink {
    private static Logger logger = LogManager.getFormatterLogger(MessageSink.class);

    private static final AtomicLong MESSAGES_SEQUENCE = new AtomicLong(0);

    private final ExecutorService executor;
    private final Disruptor<MessageEvent> disruptor;
    private final RingBuffer<MessageEvent> ringBuffer;

    private final Map<Long, CompletableFuture<GwReply>> linkReplies;
    private final InternalMessageLink leftLink;
    private final InternalMessageLink rightLink;
    private final MessageEventHandler eventHandler;

    @SuppressWarnings("unchecked")
    public MessageSink() {
        linkReplies = new ConcurrentHashMap<>();
        leftLink = new InternalMessageLink();
        rightLink = new InternalMessageLink();
        eventHandler = new MessageEventHandler();

        executor = Executors.newSingleThreadExecutor();
        disruptor = new Disruptor<>(MessageEvent::new, 1024, executor);
        disruptor.handleEventsWith(eventHandler);
        ringBuffer = disruptor.getRingBuffer();

        disruptor.start();
    }

    public MessageLink getLeftLink() {
        return leftLink;
    }

    public MessageLink getRightLink() {
        return rightLink;
    }

    private void checkStart() {
        try {
            eventHandler.waitLinks(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            // do nothing
        } catch (TimeoutException e) {
            logger.error("tried to access an unready sink");
            throw new IllegalStateException("sink is not ready yet");
        }
    }

    public void stop() {
        executor.shutdown();
        disruptor.shutdown();
    }

    private static final class MessageEvent {
        private DataMessage message;
        private MessageLink sourceLink;

        DataMessage getMessage() {
            return message;
        }

        void setMessage(DataMessage message) {
            this.message = message;
        }

        MessageLink getSourceLink() {
            return sourceLink;
        }

        void setSourceLink(MessageLink sourceLink) {
            this.sourceLink = sourceLink;
        }
    }

    private static void checkLinkArg(final GwMessage msg, final String name) {
        if (msg == null) {
            logger.error("link received a null %s", name);
            throw new NullPointerException(name);
        }
    }

    private final class InternalMessageLink implements MessageLink {
        private MessageListener listener;

        @Override
        public Future<GwReply> sendRequest(GwRequest request) {
            checkLinkArg(request, "request");
            checkStart();

            request.setSequence(MESSAGES_SEQUENCE.incrementAndGet());

            final CompletableFuture<GwReply> future = new CompletableFuture<>();
            linkReplies.put(request.getSequence(), future);

            disruptorPublish(request);

            return future;
        }

        @Override
        public void sendReply(final GwReply reply) {
            checkLinkArg(reply, "reply");
            checkStart();

            final Long m_seq = reply.getSequence();
            if (m_seq == null) {
                logger.error("received a reply without a sequence number");
                throw new IllegalArgumentException("reply without a sequence");
            }

            final CompletableFuture<GwReply> replyFuture = linkReplies.remove(m_seq);
            if (replyFuture == null) {
                logger.error("not found a message with sequence %d to send the reply", m_seq);
                throw new IllegalArgumentException("sequence " + m_seq + " not found");
            }
            replyFuture.complete(reply);
        }

        @Override
        public void broadcast(final GwNews news) {
            checkLinkArg(news, "news");
            checkStart();

            disruptorPublish(news);
        }

        @Override
        public void setUp(final MessageListener listener) {
            Validate.validState(this.listener == null, "link already set up");
            this.listener = listener;
            eventHandler.addLink();
        }

        private void disruptorPublish(final DataMessage msg) {
            final long sequence = ringBuffer.next();
            final MessageEvent event = ringBuffer.get(sequence);
            event.setMessage(msg);
            event.setSourceLink(this);
            ringBuffer.publish(sequence);
        }
    }

    private final class MessageEventHandler implements EventHandler<MessageEvent> {
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
        public void onEvent(final MessageEvent event, long sequence, boolean endOfBatch) {
            final InternalMessageLink targetLink = (event.getSourceLink() == leftLink) ? rightLink : leftLink;
            try {
                targetLink.listener.valueReceived(event.getMessage());
            } catch (Exception ignored) {
                // Any errors are silently ignored
            }
        }
    }
}
