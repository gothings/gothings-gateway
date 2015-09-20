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
}
