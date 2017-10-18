package io.github.mike10004.nanoserver;

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
 */
public class NanoResponse {

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private final NanoHTTPD.Response.IStatus status;
    private MediaType contentType;
    private Supplier<InputStream> content;
    private long contentLength;

    private NanoResponse(IStatus status) {
        this.status = checkNotNull(status);
        contentType = MediaType.OCTET_STREAM;
        content = () -> new ByteArrayInputStream(EMPTY_BYTE_ARRAY);
    }

    public NanoHTTPD.Response build() {
        return NanoHTTPD.newFixedLengthResponse(status, contentType.toString(), content.get(), contentLength);
    }

    public static NanoResponse builder(int status) {
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
        return builder(status_);
    }

    public static NanoResponse builder(NanoHTTPD.Response.IStatus status) {
        return new NanoResponse(status);
    }

    public NanoResponse content(MediaType contentType, InputStream data, long contentLength) {
        type(contentType);
        this.content = () -> data;
        this.contentLength = contentLength;
        return this;
    }

    public NanoResponse content(MediaType contentType, byte[] data) {
        return content(contentType, new ByteArrayInputStream(data), data.length);
    }

    public NanoResponse plainTextUtf8(String text) {
        return plainText(text, StandardCharsets.UTF_8);
    }

    public NanoResponse plainText(String text, Charset charset) {
        return content(MediaType.PLAIN_TEXT_UTF_8.withCharset(charset), text.getBytes(charset));
    }

    public NanoResponse raw(byte[] bytes) {
        return content(MediaType.OCTET_STREAM, bytes);
    }

    public NanoResponse type(MediaType contentType) {
        this.contentType = checkNotNull(contentType);
        return this;
    }
}
