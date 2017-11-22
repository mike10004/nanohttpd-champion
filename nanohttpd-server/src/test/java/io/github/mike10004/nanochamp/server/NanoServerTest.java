package io.github.mike10004.nanochamp.server;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import fi.iki.elonen.NanoHTTPD;
import org.apache.http.Header;
import org.apache.http.HttpMessage;
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
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NanoServerTest {

    private static final String ENCODING_GZIP = "gzip";
    private static final String ENCODING_BROTLI = "br";

    @Test
    public void basic() throws Exception {
        NanoServer server = NanoServer.builder()
                .getPath("/hello", NanoResponse.status(200).plainTextUtf8("hello"))
                .build();
        try (NanoControl control = server.startServer();
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
        String headerName = "X-Custom-Header", headerValue = "#yolo";
        NanoHTTPD.Response responseWithHeaders = NanoResponse.status(200)
                .header(headerName, headerValue)
                .plainTextUtf8("OK");
        NanoServer server = NanoServer.builder().get(responseWithHeaders).build();
        Header headers[];
        try (NanoControl ctrl = server.startServer()) {
            headers = fetch(ctrl.baseUri(), HttpMessage::getAllHeaders);
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
        Charset charset = StandardCharsets.UTF_8;
        byte[] original = "hello".getBytes(charset);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(64);
        try (OutputStream out = new GZIPOutputStream(baos)) {
            out.write(original);
        }
        byte[] gzipped = baos.toByteArray();
        NanoHTTPD.Response gzippedResponse = NanoResponse.status(200)
                .content(MediaType.PLAIN_TEXT_UTF_8.withCharset(charset), gzipped)
                .header(HttpHeaders.CONTENT_ENCODING, ENCODING_GZIP)
                .build();
        NanoServer server = NanoServer.builder()
                .get(gzippedResponse)
                .build();
        byte[] actual;
        try (NanoControl ctrl = server.startServer()) {
            actual = fetchIfOk(ctrl.baseUri());
        }
        assertArrayEquals("bytes", original, actual);
    }

    @Test
    public void brotliEncodedText() throws Exception {
        byte[] bytes = Base64.getDecoder().decode("G2MAACSCArFAOg=="); // brotli that decompresses to string of 100 'A' characters
        NanoHTTPD.Response rawResponse = NanoResponse.status(200)
                .content(MediaType.PLAIN_TEXT_UTF_8, bytes)
                .header(HttpHeaders.CONTENT_ENCODING, ENCODING_BROTLI)
                .build();
        NanoServer server = NanoServer.builder()
                .get(rawResponse)
                .build();
        byte[] actual;
        AtomicReference<Header[]> encodingHeaders = new AtomicReference<>();
        try (NanoControl ctrl = server.startServer()) {
            actual = fetch(ctrl.baseUri(), response -> {
                encodingHeaders.set(response.getHeaders(HttpHeaders.CONTENT_ENCODING));
                return EntityUtils.toByteArray(response.getEntity());
            });
        }
        Stream.of(encodingHeaders.get()).forEach(System.out::println);
        assertArrayEquals("bytes", bytes, actual);
    }

    private static byte[] fetchIfOk(URI uri) throws IOException {
        return fetch(uri, response -> {
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new IOException(response.getStatusLine().toString());
            }
            return EntityUtils.toByteArray(response.getEntity());
        });
    }

    private static <T> T fetch(URI uri, ResponseHandler<T> responseHandler) throws IOException {
        try (CloseableHttpClient client = HttpClients.createSystem()) {
            return client.execute(new HttpGet(uri), responseHandler);
        }
    }


}