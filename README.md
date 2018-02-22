[![Travis build status](https://travis-ci.org/mike10004/nanohttpd-champion.svg?branch=master)](https://travis-ci.org/mike10004/nanohttpd-champion)
[![AppVeyor build status](https://ci.appveyor.com/api/projects/status/gh1tuv64urbhfldb?svg=true)](https://ci.appveyor.com/project/mike10004/nanohttpd-champion)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.mike10004/nanohttpd-champion.svg)](https://repo1.maven.org/maven2/com/github/mike10004/nanohttpd-champion/)

# nanohttpd-champion

Use [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) like a champion.

## Maven

    <dependency>
        <groupId>com.github.mike10004</groupId>
        <artifactId>nanohttpd-server</artifactId>
        <version>0.10</version>
    </dependency>

## Usage

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
