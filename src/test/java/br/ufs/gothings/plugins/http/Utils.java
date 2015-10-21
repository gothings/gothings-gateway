package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.plugin.FutureReply;
import br.ufs.gothings.core.plugin.ReplyListener;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Wagner Macedo
 */
public class Utils {
    public static FutureReply constantReply(final GwReply reply) {
        return new ConstantReply(reply);
    }

    private static class ConstantReply implements FutureReply {
        private final GwReply value;

        public ConstantReply(final GwReply value) {
            this.value = value;
        }

        @Override
        public GwReply get() throws InterruptedException, ExecutionException {
            return value;
        }

        @Override
        public GwReply get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return value;
        }

        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public void setListener(final ReplyListener replyListener) {
            throw new UnsupportedOperationException();
        }
    }
}
