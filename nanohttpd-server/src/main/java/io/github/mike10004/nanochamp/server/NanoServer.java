package io.github.mike10004.nanochamp.server;

import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.IHTTPSession;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.Method;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;

public class NanoServer {

    private final List<RequestHandler> requestHandlers;
    private final RequestHandler defaultRequestHandler;
    @Nullable
    private NanoControl.HttpdImplFactory httpdFactory;

    private NanoServer(Builder b) {
        this(b.requestHandlers, b.defaultRequestHandler);
        httpdFactory = b.httpdImplFactory;
    }

    public NanoServer(Iterable<RequestHandler> requestHandlers, RequestHandler defaultRequestHandler) {
        this.requestHandlers = Collections.unmodifiableList(StreamSupport.stream(requestHandlers.spliterator(), false).collect(Collectors.toList()));
        this.defaultRequestHandler = requireNonNull(defaultRequestHandler);
        this.httpdFactory = null;
    }

    /**
     * Interface for handlers of requests.
     */
    public interface RequestHandler {

        /**
         * Responds to a request or ignores it if another handler should handle the request instead.
         * @param session the session
         * @return a response, or null if this handler should not handle this request
         */
        @Nullable
        NanoHTTPD.Response serve(NanoHTTPD.IHTTPSession session);

        static RequestHandler getDefault() {
            return (session) -> {
                LoggerFactory.getLogger(RequestHandler.class.getName() + ".default").debug("404 {} {}", session.getUri(), StringUtils.abbreviate(session.getQueryParameterString(), 128));
                return produceNotFoundResponse();
            };
        }
    }

    private static NanoHTTPD.Response produceNotFoundResponse() {
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain; charset=us-ascii", "404 Not Found");
    }

    static int findUnusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    public NanoControl startServer(int port) throws IOException {
        return new NanoControl(port, requestHandlers, defaultRequestHandler, httpdFactory);
    }

    public NanoControl startServer() throws IOException {
        int port = findUnusedPort();
        return startServer(port);
    }

    /**
     * Server builder. Use this builder to define how your server should respond to requests.
     * Note that you should not pre-fabricate {@link NanoHTTPD.Response} objects because each
     * instance's input stream will be exhausted after it is used the first time. That is,
     * your {@link ResponseProvider} implementations should always construct a new response
     * inside the {@link ResponseProvider#serve(ServiceRequest)} method.
     */
    public static class Builder {

        private final List<RequestHandler> requestHandlers = new ArrayList<>();
        private RequestHandler defaultRequestHandler = RequestHandler.getDefault();
        private NanoControl.HttpdImplFactory httpdImplFactory = null;

        private Builder() {}

        public Builder httpdFactory(NanoControl.HttpdImplFactory httpdFactory) {
            this.httpdImplFactory = httpdFactory;
            return this;
        }

        public Builder get(ResponseProvider responseProvider) {
            return handle(request -> request.method == Method.GET, responseProvider);
        }

        public Builder getPath(String path, ResponseProvider responseProvider) {
            return getPath(path::equals, responseProvider);
        }

        public Builder getPath(Predicate<? super String> pathPredicate, ResponseProvider responseProvider) {
            return handle(request -> request.method == Method.GET && pathPredicate.test(request.uri.getPath()), responseProvider);
        }

        public Builder handle(Predicate<? super ServiceRequest> decider, ResponseProvider responseProvider) {
            return handle(new ResponseProvider() {

                @Nullable
                @Override
                public Response serve(ServiceRequest request) {
                    if (decider.test(request)) {
                        return responseProvider.serve(request);
                    }
                    return null;
                }
            });
        }

        public Builder handle(ResponseProvider requestHandler) {
            return session(requestHandler);
        }

        public Builder session(RequestHandler requestHandler) {
            requestHandlers.add(requireNonNull(requestHandler));
            return this;
        }

        public Builder handleAll(Collection<? extends RequestHandler> requestHandlers) {
            this.requestHandlers.addAll(requestHandlers);
            return this;
        }

        public Builder setDefault(RequestHandler defaultRequestHandler) {
            this.defaultRequestHandler = requireNonNull(defaultRequestHandler);
            return this;
        }

        public NanoServer build() {
            return new NanoServer(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public interface ValueListMap<K, V> {

        Map<K, List<V>> asMap();

    }

    public static class ServiceRequest {
        public final NanoHTTPD.Method method;
        public final URI uri;
        public final ValueListMap<String, String> query;
        public final Function<String, String> headers;
        public final IHTTPSession session;

        public ServiceRequest(Method method, URI uri, ValueListMap<String, String> query, Function<String, String> headers, IHTTPSession session) {
            this.method = method;
            this.uri = uri;
            this.query = query;
            this.headers = headers;
            this.session = session;
        }

        public static ServiceRequest fromSession(IHTTPSession session) {
            return new ServiceRequest(session.getMethod(), URI.create(session.getUri()), makeMultimap(session.getParameters()), new CaseInsensitiveMapFunction<>(session.getHeaders()), session);
        }
    }

    private static <K, V> ValueListMap<K, V> makeMultimap(Map<K, ? extends Collection<V>> map) {
        List<Map.Entry<K, V>> list = new ArrayList<>();
        map.forEach((k, values) -> values.forEach(v -> list.add(new SimpleImmutableEntry<>(k, v))));
        Map<K, List<V>> m = MultimapShim.copyOf(list);
        return new ValueListMap<K, V>() {
            @Override
            public Map<K, List<V>> asMap() {
                return m;
            }
        };
    }

    /**
     * Interface of classes that provide responses to HTTP requests.
     */
    public interface ResponseProvider extends RequestHandler {

        /**
         * Produces a response to a request.
         * @param request parsed request
         * @return a response, or null if another handler should handle this request
         */
        @Nullable
        NanoHTTPD.Response serve(ServiceRequest request);

        /**
         * Serves a response for a session. This method delegates to
         * {@link #serve(ServiceRequest)}.
         * @param session the session
         * @return a response
         * @see #serve(ServiceRequest)
         */
        @Nullable
        @Override
        default NanoHTTPD.Response serve(IHTTPSession session) {
            return serve(ServiceRequest.fromSession(session));
        }

    }

    private static class CaseInsensitiveMapFunction<V> implements Function<String, V> {
        private final Map<String, V> data;

        private CaseInsensitiveMapFunction(Map<String, V> data) {
            this.data = data;
        }

        @Override
        public V apply(String key) {
            V exact = data.get(key);
            if (exact != null) {
                return exact;
            }
            return data.entrySet().stream()
                    .filter(entry -> key.equalsIgnoreCase(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst().orElse(null);
        }
    }
}
