package io.github.mike10004.nanochamp.server;

import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.nanochamp.server.NanoServer.RequestHandler;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.github.mike10004.nanochamp.server.GuavaShim.checkArgument;
import static io.github.mike10004.nanochamp.server.GuavaShim.checkState;
import static java.util.Objects.requireNonNull;

public class NanoControl implements Closeable {

    private final NanoHTTPD server;
    private final List<? extends RequestHandler> requestHandlers;
    private final AtomicLong numRequestsMatched = new AtomicLong(0L);
    private final AtomicLong numRequestsHeard = new AtomicLong(0L);

    NanoControl(int port, Iterable<? extends RequestHandler> requestHandlers, RequestHandler defaultRequestHandler) throws IOException {
        this(port, requestHandlers, defaultRequestHandler, null);
    }

    NanoControl(int port, Iterable<? extends RequestHandler> requestHandlers, RequestHandler defaultRequestHandler, HttpdImplFactory httpdFactory) throws IOException {
        checkArgument( port > 0 && port < 65536, "port " + port);
        this.requestHandlers = Collections.unmodifiableList(StreamSupport.stream(requestHandlers.spliterator(), false)
                .collect(Collectors.toList()));
        if (httpdFactory == null) {
            httpdFactory = createDefaultFactory();
        }
        server = httpdFactory.construct(this, port, defaultRequestHandler);
        server.start();
    }

    private static HttpdImplFactory createDefaultFactory() {
        return new HttpdImplFactory() {
            @Override
            public NanoHttpdImpl construct(NanoControl control, int port, RequestHandler defaultRequestHandler) {
                return control.new NanoHttpdImpl(port, defaultRequestHandler);
            }
        };
    }

    public interface HttpdImplFactory {
        NanoHttpdImpl construct(NanoControl control, int port, RequestHandler defaultRequestHandler);
    }

    public class NanoHttpdImpl extends NanoHTTPD {

        private final RequestHandler defaultRequestHandler;

        public NanoHttpdImpl(int port, RequestHandler defaultRequestHandler) {
            super(port);
            this.defaultRequestHandler = requireNonNull(defaultRequestHandler);
        }
        @Override
        public Response serve(IHTTPSession session) {
            numRequestsHeard.incrementAndGet();
            for (RequestHandler handler : NanoControl.this.requestHandlers) {
                Response response = handler.serve(session);
                if (response != null) {
                    numRequestsMatched.incrementAndGet();
                    return response;
                }
            }
            return defaultRequestHandler.serve(session);
        }

        /**
         * Checks whether response should be gzip encoded. Decides based on whether
         * the client states that it can accept gzip encoding and whether the response
         * specifies some other encoding. We override the superclass method because
         * it ignores whether an alternate encoding is specified, which results in
         * re-encoding already compressed streams. We take any existing specification of a
         * content encoding, even the "identity" encoding, as a strong signal that we
         * should avoid transforming response with an additional gzip encoding. See
         * <a href="https://github.com/NanoHttpd/nanohttpd/issues/463">nanohttpd issue #463</a>.
         * @param r the response
         * @return true iff the client accepts gzip encoding and the response does not already
         * specify an encoding
         */
        @Override
        protected boolean useGzipWhenAccepted(Response r) {
            return super.useGzipWhenAccepted(r) && !isAnyContentEncodingSpecified(r);
        }


    }

    /**
     * Checks whether the response contains a Content-Encoding header.
     * @param response the response
     * @return true iff the response contains a nonempty Content-Encoding header
     */
    static boolean isAnyContentEncodingSpecified(NanoHTTPD.Response response) {
        String contentEncoding = response.getHeader(CONTENT_ENCODING);
        return !GuavaShim.isNullOrEmpty(contentEncoding);
    }

    private static final String CONTENT_ENCODING = "content-encoding";

    @Override
    public void close() throws IOException {
        if (server.wasStarted()) {
            server.stop();
        }
    }

    public int getListeningPort() {
        return server.getListeningPort();
    }

    public HostAddress getSocketAddress() {
        checkState(server != null, "server not instantiated yet");
        return new HostAddress("localhost", getListeningPort());
    }

    /**
     * Constructs the base URI associated with this server, using scheme {@code http}
     * @return the base URI
     * @see #baseUri(String)
     */
    public URI baseUri() {
        return baseUri("http");
    }

    /**
     * Constructs the base URI associated with server. This is the URI with path {@code /}.
     * @param scheme the scheme: http or https
     * @return the base URI
     */
    public URI baseUri(String scheme) {
        checkArgument("http".equals(scheme) || "https".equals(scheme), "only 'http' or 'https' is accepted for scheme parameter");
        return URI.create(scheme + "://" + getSocketAddress() + "/");
    }

    @SuppressWarnings("unused")
    public long getNumRequestsHeard() {
        return numRequestsHeard.get();
    }

    @SuppressWarnings("unused")
    public long getNumRequestsMatched() {
        return numRequestsMatched.get();
    }

    public void flush() throws InterruptedException {
        server.flush();
    }
}
