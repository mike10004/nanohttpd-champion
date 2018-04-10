package io.github.mike10004.nanochamp.server;

import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.Header;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class NanoServerTest {

    private static final String ENCODING_GZIP = "gzip";
    private static final String ENCODING_BROTLI = "br";

    @Test
    public void basic() throws Exception {
        printTitle("basic");
        doBasicTest(NanoServer::startServer);
    }

    private static void printTitle(String title) {
        System.out.println(title);
        System.err.println(title);
    }

    @Test
    public void basic_specifyPort() throws Exception {
        printTitle("basic_specifyPort");
        int port = Tests.findPortToUse();
        doBasicTest(server -> {
            NanoControl ctrl = server.startServer(port);
            assertEquals("port", port, ctrl.getListeningPort());
            return ctrl;
        });
    }

    private interface ControlFactory {
        NanoControl createControl(NanoServer server) throws IOException;
    }

    private void doBasicTest(ControlFactory ctrlFactory ) throws IOException, URISyntaxException {
        NanoServer server = NanoServer.builder()
                .getPath("/hello", session -> NanoResponse.status(200).plainTextUtf8("hello"))
                .build();
        try (NanoControl control = ctrlFactory.createControl(server);
             CloseableHttpClient client = HttpClients.createSystem()) {
            try (CloseableHttpResponse response = client.execute(new HttpGet(new URIBuilder(control.baseUri()).setPath("/hello").build()))) {
                assertEquals("status", 200, response.getStatusLine().getStatusCode());
                assertEquals("message", "hello", EntityUtils.toString(response.getEntity()));
            }
            try (CloseableHttpResponse response = client.execute(new HttpGet(new URIBuilder(control.baseUri()).setPath("/notfound").build()))) {
                assertEquals("status", 404, response.getStatusLine().getStatusCode());
            }
        }
    }

    @Test
    public void customHeaders() throws Exception {
        printTitle("customHeaders");
        String headerName = "X-Custom-Header", headerValue = "#yolo";
        NanoServer server = NanoServer.builder().get(session -> {
            return NanoResponse.status(200)
                    .header(headerName, headerValue)
                    .plainTextUtf8("OK");
        }).build();
        Header headers[];
        try (NanoControl ctrl = server.startServer()) {
            headers = fetch(ctrl, ctrl.baseUri(), HttpMessage::getAllHeaders);
        }
        @Nullable String value = Stream.of(headers)
                .filter(header -> headerName.equalsIgnoreCase(header.getName()))
                .map(Header::getValue)
                .findFirst().orElse(null);
        assertNotNull("header not present", value);
        assertEquals("value", headerValue, value);
    }

    @Test
    public void gzipEncodedText() throws Exception {
        printTitle("gzipEncodedText");
        Charset charset = StandardCharsets.UTF_8;
        byte[] original = "hello".getBytes(charset);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(64);
        try (OutputStream out = new GZIPOutputStream(baos)) {
            out.write(original);
        }
        byte[] gzipped = baos.toByteArray();
        NanoServer server = NanoServer.builder()
                .get(session -> {
                    return NanoResponse.status(200)
                            .content(MediaType.PLAIN_TEXT_UTF_8.withCharset(charset), gzipped)
                            .header(HttpHeaders.CONTENT_ENCODING, ENCODING_GZIP)
                            .build();
                }).build();
        byte[] actual;
        try (NanoControl ctrl = server.startServer()) {
            actual = fetchIfOk(ctrl, ctrl.baseUri());
        }
        assertArrayEquals("bytes", original, actual);
    }

    @Test
    public void brotliEncodedText() throws Exception {
        printTitle("brotliEncodedText");
        byte[] bytes = Base64.getDecoder().decode("G2MAACSCArFAOg=="); // brotli that decompresses to string of 100 'A' characters
        NanoServer server = NanoServer.builder()
                .get(session -> {
                    return NanoResponse.status(200)
                            .content(MediaType.PLAIN_TEXT_UTF_8, bytes)
                            .header(HttpHeaders.CONTENT_ENCODING, ENCODING_BROTLI)
                            .build();
                }).build();
        byte[] actual;
        AtomicReference<Header[]> encodingHeaders = new AtomicReference<>();
        try (NanoControl ctrl = server.startServer()) {
            actual = fetch(ctrl, ctrl.baseUri(), response -> {
                encodingHeaders.set(response.getHeaders(HttpHeaders.CONTENT_ENCODING));
                return EntityUtils.toByteArray(response.getEntity());
            });
        }
        Stream.of(encodingHeaders.get()).forEach(System.out::println);
        assertArrayEquals("bytes", bytes, actual);
    }

    private static byte[] fetchIfOk(NanoControl ctrl, URI uri) throws IOException {
        return fetch(ctrl, uri, response -> {
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new IOException(response.getStatusLine().toString());
            }
            return EntityUtils.toByteArray(response.getEntity());
        });
    }

    /*
     * The server logs an error if the entity is not consumed. Some response handlers
     * only go for the headers. This method consumes the entity after delegating to the
     * handler that produces the desired return value.
     * This doesn't eliminate the error all of the time, but I don't know what else to do.
     */
    private static <T> T fetch(NanoControl ctrl, URI uri, ResponseHandler<T> responseHandler) throws IOException {
        try (CloseableHttpClient client = HttpClients.createSystem()) {
            T thing;
            try (CloseableHttpResponse response = client.execute(new HttpGet(uri))) {
                thing = responseHandler.handleResponse(response);
                ctrl.flush();
                EntityUtils.consume(response.getEntity());
            } finally {
                System.err.println("http response closed");
            }
            return thing;
        } catch (InterruptedException e) {
            throw new IOException(e);
        } finally {
            System.err.println("http client closed");
        }
    }

    private static final boolean debug = true;
    private static final AtomicInteger counter = new AtomicInteger(0);

    private static class ResponsePackage {
        public final String contentType;
        public final byte[] data;
        @Nullable
        public final Long contentLength;

        private ResponsePackage(String contentType, byte[] data, @Nullable Long contentLength) {
            this.contentType = contentType;
            this.data = data;
            this.contentLength = contentLength;
        }

        public static ResponseHandler<ResponsePackage> handler() {
            return response -> {
                String contentType = getFirstValue(response, HttpHeaders.CONTENT_TYPE);
                byte[] data = EntityUtils.toByteArray(response.getEntity());
                @Nullable Long contentLength = maybeParseLong(getFirstValue(response, HttpHeaders.CONTENT_LENGTH));
                if (debug) {
                    int count = counter.incrementAndGet();
                    dumpHeaders(count, response.getAllHeaders(), System.out);
                }
                return new ResponsePackage(contentType, data, contentLength);
            };
        }
    }

    private static void dumpHeaders(int index, Header[] allHeaders, @SuppressWarnings("SameParameterValue") PrintStream out) {
        out.format("[%d] %d headers%n", index, allHeaders.length);
        Stream.of(allHeaders).forEach(header -> {
            out.format("[%d] %s: %s%n", index, header.getName(), header.getValue());
        });

    }

    @Nullable
    private static String getFirstValue(HttpResponse response, String name) {
        return Stream.of(response.getHeaders(name)).findAny().map(Header::getValue).orElse(null);
    }

    @Nullable
    private static Long maybeParseLong(@Nullable String token) {
        if (token != null) {
            return Long.valueOf(token);
        }
        return null;
    }

    @Test
    public void defaultResponseSpecifiesCharset() throws Exception {
        printTitle("defaultResponseSpecifiesCharset");
        System.out.format("for reference: %s%n", MediaType.PLAIN_TEXT_UTF_8.withCharset(StandardCharsets.US_ASCII));
        NanoServer server = NanoServer.builder().build();
        ResponsePackage pkg;
        try (NanoControl ctrl = server.startServer()) {
            pkg = fetch(ctrl, ctrl.baseUri(), ResponsePackage.handler());
        }
        assertNotNull("contentType", pkg.contentType);
        MediaType mediaType = MediaType.parse(pkg.contentType);
        Preconditions.checkState(mediaType.is(MediaType.ANY_TEXT_TYPE), "this unit test expects the " +
                "default response content type to be a text type because otherwise asking whether the charset is " +
                "present might not make any sense at all");
        assertTrue("expect charset present in " + mediaType, mediaType.charset().isPresent());
        String content = new String(pkg.data, mediaType.charset().get());
        System.out.format("%d bytes, %d characters in \"%s\"%n", pkg.data.length, content.length(), StringEscapeUtils.escapeJava(StringUtils.abbreviate(content, 512)));
    }

    @Test
    public void sameDefaultResponseReceivedMultipleTimes() throws Exception {
        printTitle("sameDefaultResponse");
        System.out.format("for reference: %s%n", MediaType.PLAIN_TEXT_UTF_8.withCharset(StandardCharsets.US_ASCII));
        NanoServer server = NanoServer.builder().build();
        ResponsePackage pkg;
        int numTries = 12;
        String expectedText = "404 Not Found";
        try (NanoControl ctrl = server.startServer()) {
            for (int i = 0; i < numTries; i++) {
                pkg = fetch(ctrl, ctrl.baseUri(), ResponsePackage.handler());
                assertNotNull("contentType", pkg.contentType);
                MediaType mediaType = MediaType.parse(pkg.contentType);
                assertTrue("contentType charset", mediaType.charset().isPresent());
                String content = new String(pkg.data, mediaType.charset().get());
                System.out.format("%2d: %d bytes, %d characters in \"%s\"%n", i + 1, pkg.data.length, content.length(), StringEscapeUtils.escapeJava(StringUtils.abbreviate(content, 512)));
                assertEquals("content should be same each time", expectedText, content);
            }
        }
    }
}