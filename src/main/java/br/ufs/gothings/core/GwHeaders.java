package br.ufs.gothings.core;

import br.ufs.gothings.core.message.ComplexHeader;
import br.ufs.gothings.core.message.Operation;
import br.ufs.gothings.core.message.Header;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Supplier;

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

    /* Header fields */

    public Header<Operation> operation() {
        return getHeader(_H_.OPERATION, Operation.class);
    }

    public Header<String> path() {
        return getHeader(_H_.PATH, String.class);
    }

    public Header<String> contentType() {
        return getHeader(_H_.CONTENT_TYPE, String.class);
    }

    public ComplexHeader<String> expectedTypes() {
        return getComplexHeader(_H_.EXPECTED_TYPES, String.class, LinkedHashSet::new);
    }

    /* Internal use */

    @SuppressWarnings({"unchecked", "unused"})
    private <T> Header<T> getHeader(_H_ key, Class<T> type) {
        return (Header<T>) map.computeIfAbsent(key, k -> new Header<>());
    }

    @SuppressWarnings({"unchecked", "unused"})
    private <T> ComplexHeader<T> getComplexHeader(_H_ key, Class<T> type, Supplier<Collection<T>> supplier) {
        return (ComplexHeader<T>) map.computeIfAbsent(key, k -> new ComplexHeader<>(supplier.get()));
    }
}
