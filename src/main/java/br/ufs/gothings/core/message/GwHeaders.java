package br.ufs.gothings.core.message;

import br.ufs.gothings.core.message.headers.Operation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;

/**
 * @author Wagner Macedo
 */
public class GwHeaders {
    private static class Holder {
        private Operation operation;
        private String target;
        private String path;
        private String contentType;
        private Set<String> expectedTypes;
        private int qos;
    }

    private final Holder holder;

    public GwHeaders() {
        holder = new Holder();
    }

    private GwHeaders(final Holder holder) {
        this.holder = holder;
    }

    public Operation getOperation() {
        return holder.operation;
    }

    public void setOperation(final Operation operation) {
        holder.operation = operation;
    }

    public String getTarget() {
        return holder.target;
    }

    public void setTarget(final String target) {
        holder.target = target;
    }

    public String getPath() {
        return holder.path;
    }

    public void setPath(final String path) {
        holder.path = path;
    }

    public String getContentType() {
        return holder.contentType;
    }

    public void setContentType(final String contentType) {
        holder.contentType = contentType;
    }

    public Collection<String> getExpectedTypes() {
        return holder.expectedTypes == null ? emptySet() : unmodifiableSet(holder.expectedTypes);
    }

    public void setExpectedTypes(final Collection<String> expectedTypes) {
        if (!(expectedTypes == null || expectedTypes.isEmpty())) {
            if (holder.expectedTypes != null) {
                holder.expectedTypes.clear();
            } else {
                holder.expectedTypes = new LinkedHashSet<>();
            }
            expectedTypes.forEach(holder.expectedTypes::add);
        }
    }

    public void addExpectedType(final String expectedType) {
        if (expectedType != null) {
            if (holder.expectedTypes == null) {
                setExpectedTypes(Collections.singletonList(expectedType));
            } else {
                holder.expectedTypes.add(expectedType);
            }
        }
    }

    public int getQoS() {
        return holder.qos;
    }

    public void setQoS(final int qos) {
        holder.qos = qos;
    }

    public final GwHeaders asReadOnly() {
        return new UnmodifiableHeaders(this);
    }

    private static final class UnmodifiableHeaders extends GwHeaders {
        public UnmodifiableHeaders(final GwHeaders wrapped) {
            super(wrapped.holder);
        }

        @Override
        public void setOperation(final Operation operation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setTarget(final String target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPath(final String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setContentType(final String contentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setExpectedTypes(final Collection<String> expectedTypes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addExpectedType(final String expectedType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setQoS(final int qos) {
            throw new UnsupportedOperationException();
        }
    }
}
