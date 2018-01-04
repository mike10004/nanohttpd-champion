package io.github.mike10004.nanochamp.server;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.nanochamp.server.NanoServer.RequestHandler;
import org.apache.commons.lang3.StringUtils;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class NanoControl implements Closeable {

    private final NanoHTTPD server;
    private final ImmutableList<? extends RequestHandler> requestHandlers;
    private final AtomicLong numRequestsMatched = new AtomicLong(0L);
    private final AtomicLong numRequestsHeard = new AtomicLong(0L);

    NanoControl(int port, Iterable<? extends RequestHandler> requestHandlers, RequestHandler defaultRequestHandler) throws IOException {
        this(port, requestHandlers, defaultRequestHandler, null);
    }

    NanoControl(int port, Iterable<? extends RequestHandler> requestHandlers, RequestHandler defaultRequestHandler, HttpdImplFactory httpdFactory) throws IOException {
        checkArgument( port > 0 && port < 65536, "port %s", port);
        this.requestHandlers = ImmutableList.copyOf(requestHandlers);
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
            this.defaultRequestHandler = checkNotNull(defaultRequestHandler);
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

        @Override
        public void setAsyncRunner(AsyncRunner asyncRunner) {
            super.setAsyncRunner(asyncRunner);
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
     * @see HttpHeaders#CONTENT_ENCODING
     */
    static boolean isAnyContentEncodingSpecified(NanoHTTPD.Response response) {
        String contentEncoding = response.getHeader(HttpHeaders.CONTENT_ENCODING);
        return !Strings.isNullOrEmpty(contentEncoding);
    }

    @Override
    public void close() throws IOException {
        if (server.wasStarted()) {
            server.stop();
        }
    }

    public int getListeningPort() {
        return server.getListeningPort();
    }

    public HostAndPort getSocketAddress() {
        checkState(server != null, "server not instantiated yet");
        return HostAndPort.fromParts("localhost", getListeningPort());
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
        checkArgument("http".equals(scheme) || "https".equals(scheme), "only 'http' or 'https' is accepted for scheme parameter, not %s", StringUtils.abbreviate(scheme, 16));
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

}
