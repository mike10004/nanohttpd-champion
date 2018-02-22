package io.github.mike10004.nanochamp.repackaged.fi.iki.elonen;

public interface FlushManager {

    interface FlushTicket extends AutoCloseable {
        @Override
        void close();
    }

    FlushTicket open();

    void flush() throws InterruptedException;

}
