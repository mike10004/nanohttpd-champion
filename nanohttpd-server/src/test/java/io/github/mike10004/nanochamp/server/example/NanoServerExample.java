package io.github.mike10004.nanochamp.server.example;

import java.io.InputStream;
import java.nio.charset.Charset;

import io.github.mike10004.nanochamp.server.NanoControl;
import io.github.mike10004.nanochamp.server.NanoResponse;
import io.github.mike10004.nanochamp.server.NanoServer;

public class NanoServerExample {

    public static void main(String[] args) throws Exception {
        Charset charset = Charset.forName("UTF-8");
        NanoServer server = NanoServer.builder()
                .get(NanoResponse.status(200).plainText("hello, world", charset))
                .build();
        try (NanoControl control = server.startServer();
            InputStream in = control.baseUri().toURL().openStream()) {
            byte[] buffer = new byte[1024];
            int r = in.read(buffer);
            String content = new String(buffer, 0, r, charset);
            System.out.println(content); // hello, world
        }
    }
}
