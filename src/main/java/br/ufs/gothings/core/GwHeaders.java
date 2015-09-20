package br.ufs.gothings.core;

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
    private Operation operation;
    private String source;
    private String target;
    private String path;
    private String contentType;
    private Set<String> expectedTypes;
    private int qos;

    public Operation getOperation() {
        return operation;
    }

    public void setOperation(final Operation operation) {
        this.operation = operation;
    }

    public String getSource() {
        return source;
    }

    public void setSource(final String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(final String target) {
        this.target = target;
    }

    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }

    public Collection<String> getExpectedTypes() {
        return expectedTypes == null ? emptySet() : unmodifiableSet(expectedTypes);
    }

    public void setExpectedTypes(final Collection<String> expectedTypes) {
        if (!(expectedTypes == null || expectedTypes.isEmpty())) {
            if (this.expectedTypes != null) {
                this.expectedTypes.clear();
            } else {
                this.expectedTypes = new LinkedHashSet<>();
            }
            expectedTypes.forEach(this.expectedTypes::add);
        }
    }

    public void addExpectedType(final String expectedType) {
        if (expectedType != null) {
            if (this.expectedTypes == null) {
                setExpectedTypes(Collections.singletonList(expectedType));
            } else {
                this.expectedTypes.add(expectedType);
            }
        }
    }

    public int getQoS() {
        return qos;
    }

    public void setQoS(final int qos) {
        this.qos = qos;
    }

    public final GwHeaders asReadOnly() {
        return new UnmodifiableHeaders(this);
    }

    private static final class UnmodifiableHeaders extends GwHeaders {
        private final GwHeaders wrapped;

        public UnmodifiableHeaders(GwHeaders wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public String getContentType() {
            return wrapped.getContentType();
        }

        @Override
        public Collection<String> getExpectedTypes() {
            return wrapped.getExpectedTypes();
        }

        @Override
        public Operation getOperation() {
            return wrapped.getOperation();
        }

        @Override
        public String getPath() {
            return wrapped.getPath();
        }

        @Override
        public int getQoS() {
            return wrapped.getQoS();
        }

        @Override
        public String getSource() {
            return wrapped.getSource();
        }

        @Override
        public String getTarget() {
            return wrapped.getTarget();
        }

        @Override
        public void setTarget(final String target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setSource(final String source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setQoS(final int qos) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPath(final String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setOperation(final Operation operation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setExpectedTypes(final Collection<String> expectedTypes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setContentType(final String contentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addExpectedType(final String expectedType) {
            throw new UnsupportedOperationException();
        }
    }
}
