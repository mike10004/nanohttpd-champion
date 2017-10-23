package io.github.mike10004.nanochamp.server;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.nanochamp.server.NanoServer.RequestHandler;
import org.apache.http.client.utils.URIBuilder;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class NanoControl implements Closeable {

    private final NanoHTTPD server;
    private final ImmutableList<? extends RequestHandler> requestHandlers;
    private final AtomicLong numRequestsMatched = new AtomicLong(0L);
    private final AtomicLong numRequestsHeard = new AtomicLong(0L);

    NanoControl(Iterable<? extends RequestHandler> requestHandlers, RequestHandler defaultRequestHandler) throws IOException {
        this.requestHandlers = ImmutableList.copyOf(requestHandlers);
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        checkState( port > 0 && port < 65536, "port %s", port);
        server = new NanoHttpdImpl(port, defaultRequestHandler);
        server.start();
    }

    private class NanoHttpdImpl extends NanoHTTPD {

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

        /**
         * Checks whether response should be gzip encoded. Decides based on whether
         * the client states that it can accept gzip encoding and whether the response
         * specifies some other encoding. We override the superclass method because
         * it ignores whether an alternate encoding is specified, which results in
         * re-encoding already compressed streams. We take any existing specification of a
         * content encoding, even the "identity" encoding, as a strong signal that we
         * should avoid transforming response with an additional gzip encoding.
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

    public URIBuilder buildUri() {
        try {
            return new URIBuilder("http://" + getSocketAddress() + "/");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public long getNumRequestsHeard() {
        return numRequestsHeard.get();
    }

    public long getNumRequestsMatched() {
        return numRequestsMatched.get();
    }

}
