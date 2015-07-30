package br.ufs.gothings.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * @author Wagner Macedo
 */
public class GwHeaders {
    /* Header identifiers */
    private enum _H_ {
        OPERATION,
        PATH,
        CONTENT_TYPE,
        EXPECTED_TYPES,
    }

    private final Map<_H_, Object> map;

    public GwHeaders() {
        map = new HashMap<>();
    }

    public void operation(String operation) {
        map.put(_H_.OPERATION, operation);
    }

    public String operation() {
        return (String) map.get(_H_.OPERATION);
    }

    public void path(String path) {
        map.put(_H_.PATH, path);
    }

    public String path() {
        return (String) map.get(_H_.PATH);
    }

    public void contentType(String contentType) {
        map.put(_H_.CONTENT_TYPE, contentType);
    }

    public String contentType() {
        return (String) map.get(_H_.CONTENT_TYPE);
    }

    @SuppressWarnings("unchecked")
    public Collection<String> expectedTypes() {
        return (Collection<String>) map.computeIfAbsent(_H_.EXPECTED_TYPES, k -> new LinkedHashSet<String>());
    }
}
