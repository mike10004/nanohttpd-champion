package io.github.mike10004.nanochamp.server;

import com.google.common.net.MediaType;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.IStatus;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Builder for responses. Builds instances of {@link NanoHTTPD.Response}.
 * Provides a response-construction API that is intuitive and compact for 90%
 * of use cases and verbose but flexible for the other 10%.
 */
public class NanoResponse {

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private final NanoHTTPD.Response.IStatus status;
    private MediaType contentType;
    private Supplier<? extends InputStream> content;
    private long contentLength;

    private NanoResponse(IStatus status) {
        this.status = checkNotNull(status);
        contentType = MediaType.OCTET_STREAM;
        content = () -> new ByteArrayInputStream(EMPTY_BYTE_ARRAY);
    }

    public NanoHTTPD.Response build() {
        return NanoHTTPD.newFixedLengthResponse(status, contentType.toString(), content.get(), contentLength);
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

    public NanoHTTPD.Response content(MediaType contentType, InputStream data, long contentLength) {
        return content(contentType, () -> data, contentLength);
    }

    private NanoHTTPD.Response content(MediaType contentType, Supplier<? extends InputStream> data, long contentLength) {
        type(contentType);
        this.content = data;
        this.contentLength = contentLength;
        return build();
    }

    public  NanoHTTPD.Response content(MediaType contentType, byte[] data) {
        return content(contentType, new ByteArrayInputStream(data), data.length);
    }

    public NanoHTTPD.Response content(MediaType contentType, String data, Charset charset) {
        return content(contentType, data.getBytes(charset));
    }

    public NanoHTTPD.Response plainTextUtf8(String text) {
        return plainText(text, StandardCharsets.UTF_8);
    }

    public NanoHTTPD.Response plainText(String text, Charset charset) {
        return content(MediaType.PLAIN_TEXT_UTF_8.withCharset(charset), text.getBytes(charset));
    }

    public NanoResponse type(MediaType contentType) {
        this.contentType = checkNotNull(contentType);
        return this;
    }

    public NanoHTTPD.Response jsonUtf8(String json) {
        return json(json, StandardCharsets.UTF_8);
    }

    public NanoHTTPD.Response json(String json, Charset charset) {
        return content(MediaType.JSON_UTF_8.withCharset(charset), json, charset);
    }
}
