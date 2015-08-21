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
    private enum Name {
        OPERATION,
        PATH,
        CONTENT_TYPE,
        EXPECTED_TYPES,
    }

    private final Map<Name, Object> map;

    public GwHeaders() {
        map = new HashMap<>();
    }

    /* Header fields */

    public Header<Operation> operationHeader() {
        return getHeader(Name.OPERATION, Operation.class);
    }

    public Header<String> pathHeader() {
        return getHeader(Name.PATH, String.class);
    }

    public Header<String> contentTypeHeader() {
        return getHeader(Name.CONTENT_TYPE, String.class);
    }

    public ComplexHeader<String> expectedTypesHeader() {
        return getComplexHeader(Name.EXPECTED_TYPES, String.class, LinkedHashSet::new);
    }

    /* Internal use */

    @SuppressWarnings({"unchecked", "unused"})
    private <T> Header<T> getHeader(Name key, Class<T> type) {
        return (Header<T>) map.computeIfAbsent(key, k -> new Header<>());
    }

    @SuppressWarnings({"unchecked", "unused"})
    private <T> ComplexHeader<T> getComplexHeader(Name key, Class<T> type, Supplier<Collection<T>> supplier) {
        return (ComplexHeader<T>) map.computeIfAbsent(key, k -> new ComplexHeader<>(supplier.get()));
    }
}
