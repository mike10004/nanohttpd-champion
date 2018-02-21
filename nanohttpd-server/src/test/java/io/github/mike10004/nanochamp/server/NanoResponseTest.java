package io.github.mike10004.nanochamp.server;

import com.google.common.net.MediaType;
import io.github.mike10004.nanochamp.repackaged.fi.iki.elonen.NanoHTTPD;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class NanoResponseTest {

    @Test
    public void buildAsciiTextResponse() throws Exception {
        Charset charset = StandardCharsets.US_ASCII;
        NanoHTTPD.Response response = NanoResponse.status(200).plainText("hello", charset);
        MediaType contentType = MediaType.parse(response.getMimeType());
        assertEquals("charset", charset, contentType.charset().orNull());
        assertEquals("content-type", MediaType.PLAIN_TEXT_UTF_8.withCharset(charset), contentType);
    }

    @Test(expected = NullPointerException.class)
    public void disallowTextResponseWithoutCharset() {
        NanoResponse.status(200).plainText("hello", null);
    }
}