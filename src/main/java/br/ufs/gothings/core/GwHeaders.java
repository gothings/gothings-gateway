package br.ufs.gothings.core;

import br.ufs.gothings.core.message.headers.ComplexHeader;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.message.headers.Header;

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
        SOURCE,
        TARGET,
        OPERATION,
        PATH,
        CONTENT_TYPE,
        EXPECTED_TYPES,
        QOS,
    }

    private final Map<Name, Object> map;

    public GwHeaders() {
        map = new HashMap<>();
    }

    /* Header fields */

    public Header<String> sourceHeader() {
        return getHeader(Name.SOURCE, String.class);
    }

    public Header<String> targetHeader() {
        return getHeader(Name.TARGET, String.class);
    }

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

    public Header<Integer> qosHeader() {
        return getHeader(Name.QOS, Integer.class);
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
