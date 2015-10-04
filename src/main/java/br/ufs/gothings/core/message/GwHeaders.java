package br.ufs.gothings.core.message;

import br.ufs.gothings.core.common.ReadOnlyException;
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
    public static final GwHeaders EMPTY = new GwHeaders().readOnly();

    private static class Holder {
        private Operation operation;
        private String target;
        private String path;
        private String contentType;
        private Set<String> expectedTypes;
        private int qos;
    }

    private final Holder holder;
    private volatile boolean readonly = false;

    public GwHeaders() {
        holder = new Holder();
    }

    public Operation getOperation() {
        return holder.operation;
    }

    public void setOperation(final Operation operation) {
        if (readonly) {
            throw new ReadOnlyException();
        }
        holder.operation = operation;
    }

    public String getTarget() {
        return holder.target;
    }

    public void setTarget(final String target) {
        if (readonly) {
            throw new ReadOnlyException();
        }
        holder.target = target;
    }

    public String getPath() {
        return holder.path;
    }

    public void setPath(final String path) {
        if (readonly) {
            throw new ReadOnlyException();
        }
        holder.path = path;
    }

    public String getContentType() {
        return holder.contentType;
    }

    public void setContentType(final String contentType) {
        if (readonly) {
            throw new ReadOnlyException();
        }
        holder.contentType = contentType;
    }

    public Collection<String> getExpectedTypes() {
        return holder.expectedTypes == null ? emptySet() : unmodifiableSet(holder.expectedTypes);
    }

    public void setExpectedTypes(final Collection<String> expectedTypes) {
        if (readonly) {
            throw new ReadOnlyException();
        }

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
        if (readonly) {
            throw new ReadOnlyException();
        }

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
        if (readonly) {
            throw new ReadOnlyException();
        }
        holder.qos = qos;
    }

    public final GwHeaders readOnly() {
        readonly = true;
        return this;
    }
}
