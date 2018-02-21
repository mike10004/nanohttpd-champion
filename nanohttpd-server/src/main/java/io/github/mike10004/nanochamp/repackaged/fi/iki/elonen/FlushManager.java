package io.github.mike10004.nanochamp.repackaged.fi.iki.elonen;

import java.util.concurrent.Phaser;

public interface FlushManager {

    interface FlushTicket extends java.lang.AutoCloseable {
        @Override
        void close();
    }

    FlushTicket open();

    void flush() throws InterruptedException;

}
