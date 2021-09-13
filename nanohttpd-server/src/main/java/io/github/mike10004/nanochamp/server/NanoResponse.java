package io.github.mike10004.nanochamp.server;

import com.google.common.net.MediaType;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD.Response.IStatus;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Builder for responses. Builds instances of {@link NanoHTTPD.Response}.
 * Provides a response-construction API that is intuitive and compact for 90%
 * of use cases and verbose but flexible for the other 10%. Note that response
 * instances are single-use (because their input stream is exhausted when served).
 */
@SuppressWarnings("unused")
public class NanoResponse {

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private final NanoHTTPD.Response.IStatus status;
    private MediaType contentType;
    private Supplier<? extends InputStream> content;
    private long contentLength;
    private final List<Map.Entry<String, String>> headers;

    private NanoResponse(IStatus status) {
        this.status = requireNonNull(status);
        contentType = MediaType.OCTET_STREAM;
        content = () -> new ByteArrayInputStream(EMPTY_BYTE_ARRAY);
        headers = new ArrayList<>();
    }

    public NanoHTTPD.Response build() {
        NanoHTTPD.Response response = NanoHTTPD.newFixedLengthResponse(status, contentType.toString(), content.get(), contentLength);
        headers.forEach(entry -> {
            response.addHeader(entry.getKey(), entry.getValue());
        });
        return response;
    }

    public static NanoResponse status(int status) {
        NanoHTTPD.Response.IStatus status_ = NanoHTTPD.Response.Status.lookup(status);
        if (status_ == null) {
            status_ = new NanoHTTPD.Response.IStatus() {

                @Override
                public String getDescription() {
                    return "user-defined error";
                }

                @Override
                public int getRequestStatus() {
                    return status;
                }
            };
        }
        return status(status_);
    }

    public static NanoResponse status(NanoHTTPD.Response.IStatus status) {
        return new NanoResponse(status);
    }

    public NanoResponse content(MediaType contentType, InputStream data, long contentLength) {
        return content(contentType, () -> data, contentLength);
    }

    private NanoResponse content(MediaType contentType, Supplier<? extends InputStream> data, long contentLength) {
        type(contentType);
        this.content = data;
        this.contentLength = contentLength;
        return this;
    }

    public NanoResponse content(MediaType contentType, byte[] data) {
        return content(contentType, new ByteArrayInputStream(data), data.length);
    }

    /**
     * @deprecated use {@link #content(MediaType, String)} and specify charset in media type
     */
    @Deprecated
    public NanoResponse content(MediaType contentType, String data, Charset charset) {
        return content(contentType, data.getBytes(charset));
    }

    public NanoResponse content(MediaType contentType, String data) {
        if (!contentType.charset().isPresent()) {
            throw new IllegalArgumentException("content type must specify charset if data is string");
        }
        Charset charset = contentType.charset().get();
        return content(contentType, data.getBytes(charset));
    }

    public NanoHTTPD.Response plainTextUtf8(String text) {
        return plainText(text, StandardCharsets.UTF_8);
    }

    public NanoHTTPD.Response plainText(String text, Charset charset) {
        return content(MediaType.PLAIN_TEXT_UTF_8.withCharset(charset), text.getBytes(charset)).build();
    }

    @SuppressWarnings("UnusedReturnValue")
    public NanoResponse type(MediaType contentType) {
        this.contentType = requireNonNull(contentType);
        return this;
    }

    public NanoHTTPD.Response jsonUtf8(String json) {
        return json(json, StandardCharsets.UTF_8);
    }

    public NanoHTTPD.Response json(String json, Charset charset) {
        return content(MediaType.JSON_UTF_8.withCharset(charset), json).build();
    }

    public NanoResponse header(String name, String value) {
        headers.add(new AbstractMap.SimpleImmutableEntry<>(name, value));
        return this;
    }

    public NanoHTTPD.Response htmlUtf8(String htmlText) {
        return html(htmlText, StandardCharsets.UTF_8);
    }

    public NanoHTTPD.Response html(String htmlText, Charset charset) {
        return content(MediaType.HTML_UTF_8.withCharset(charset), htmlText).build();
    }

    public NanoHTTPD.Response png(byte[] bytes) {
        return content(MediaType.PNG, bytes).build();
    }

    public NanoHTTPD.Response zip(byte[] bytes) {
        return content(MediaType.ZIP, bytes).build();
    }

    public NanoHTTPD.Response gif(byte[] bytes) {
        return content(MediaType.GIF, bytes).build();
    }

    public NanoHTTPD.Response jpeg(byte[] bytes) {
        return content(MediaType.JPEG, bytes).build();
    }

    private NanoHTTPD.Response text(MediaType mediaType, String content) {
        return content(mediaType, content).build();
    }

    public NanoHTTPD.Response css(String text, Charset charset) {
        return text(MediaType.CSS_UTF_8.withCharset(charset), text);
    }

    public NanoHTTPD.Response csv(String text, Charset charset) {
        return text(MediaType.CSV_UTF_8.withCharset(charset), text);
    }

    public NanoHTTPD.Response javascript(String text, Charset charset) {
        return text(MediaType.JAVASCRIPT_UTF_8.withCharset(charset), text);
    }

    public NanoHTTPD.Response octetStream(byte[] data) {
        return content(MediaType.OCTET_STREAM, data).build();
    }

    /**
     * Forwards to the repackaged {@code NanoHTTPD} method.
     * @return a response
     * @see NanoHTTPD#newFixedLengthResponse(String)
     */
    public static NanoHTTPD.Response newFixedLengthResponse(String msg) {
        return NanoHTTPD.newFixedLengthResponse(msg);
    }

    /**
     * Forwards to the repackaged {@code NanoHTTPD} method.
     * @return a response
     * @see NanoHTTPD#newFixedLengthResponse(IStatus, String, String)
     */
    public static NanoHTTPD.Response newFixedLengthResponse(IStatus status, String mimeType, String txt) {
        return NanoHTTPD.newFixedLengthResponse(status, mimeType, txt);
    }
    /**
     * Forwards to the repackaged {@code NanoHTTPD} method.
     * @return a response
     * @see NanoHTTPD#newFixedLengthResponse(IStatus, String, InputStream, long)
     */
    public static NanoHTTPD.Response newFixedLengthResponse(IStatus status, String mimeType, InputStream data, long length) {
        return NanoHTTPD.newFixedLengthResponse(status, mimeType, data, length);
    }
}
