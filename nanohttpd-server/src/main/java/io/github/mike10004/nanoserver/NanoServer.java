package io.github.mike10004.nanoserver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class NanoServer {

    private final ImmutableList<RequestHandler> requestHandlers;
    private final RequestHandler defaultRequestHandler;

    public NanoServer(Iterable<RequestHandler> requestHandlers, RequestHandler defaultRequestHandler) {
        this.requestHandlers = ImmutableList.copyOf(requestHandlers);
        this.defaultRequestHandler = checkNotNull(defaultRequestHandler);
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
                return NOT_FOUND_RESPONSE;
            };
        }
    }

    private static final NanoHTTPD.Response NOT_FOUND_RESPONSE = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "404 Not Found");

    public NanoControl startServer() throws IOException {
        return new NanoControl(requestHandlers, defaultRequestHandler);
    }

    public static class Builder {
        private List<RequestHandler> requestHandlers = new ArrayList<>();

        private Builder() {}

        public Builder get(ResponseProvider responseProvider) {
            return handle(request -> request.method == Method.GET, responseProvider);
        }

        public Builder getPath(String path, ResponseProvider responseProvider) {
            return getPath(path::equals, responseProvider);
        }

        public Builder getPath(Predicate<? super String> pathPredicate, ResponseProvider responseProvider) {
            return handle(request -> request.method == Method.GET && pathPredicate.test(request.uri.getPath()), responseProvider);
        }

        public Builder get(NanoHTTPD.Response response) {
            return handle(request -> request.method == Method.GET, request -> response);
        }

        public Builder getPath(String path, NanoHTTPD.Response response) {
            return getPath(path::equals, request -> response);
        }

        public Builder getPath(Predicate<? super String> pathPredicate, NanoHTTPD.Response response) {
            return handle(request -> request.method == Method.GET && pathPredicate.test(request.uri.getPath()), request -> response);
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
            requestHandlers.add(checkNotNull(requestHandler));
            return this;
        }

        public Builder handleAll(Collection<? extends RequestHandler> requestHandlers) {
            this.requestHandlers.addAll(requestHandlers);
            return this;
        }

        public NanoServer build() {
            return new NanoServer(requestHandlers, RequestHandler.getDefault());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class ServiceRequest {
        public final NanoHTTPD.Method method;
        public final URI uri;
        public final ImmutableMultimap<String, String> query;
        public final Function<String, String> headers;
        public final IHTTPSession session;

        public ServiceRequest(Method method, URI uri, ImmutableMultimap<String, String> query, Function<String, String> headers, IHTTPSession session) {
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

    private static <K, V> ImmutableMultimap<K, V> makeMultimap(Map<K, ? extends Collection<V>> map) {
        List<Map.Entry<K, V>> list = new ArrayList<>();
        map.forEach((k, values) -> values.forEach(v -> list.add(new SimpleImmutableEntry<>(k, v))));
        return ImmutableMultimap.copyOf(list);
    }

    public interface ResponseProvider extends RequestHandler {

        @Nullable
        Response serve(ServiceRequest request);

        @Nullable
        @Override
        default Response serve(IHTTPSession session) {
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
