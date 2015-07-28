package br.ufs.gothings.core;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Wagner Macedo
 */
public class GwHeaders {
    private String operation;
    private String path;
    private String forwardType;
    private final Set<String> expectedTypes;

    public GwHeaders() {
        expectedTypes = new LinkedHashSet<>();
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setForwardType(String forwardType) {
        this.forwardType = forwardType;
    }

    public String getForwardType() {
        return forwardType;
    }

    public Set<String> expectedTypes() {
        return expectedTypes;
    }
}
