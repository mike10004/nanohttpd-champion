package io.github.mike10004.nanochamp.repackaged.fi.iki.elonen;

import java.util.concurrent.Phaser;

public class PhaserFlushManager implements FlushManager {

    private final Phaser phaser = new Phaser();

    @Override
    public FlushTicket open() {
        return new PhaserPartyTicket();
    }

    @Override
    public void flush() throws InterruptedException {
        int phase = phaser.getPhase();
        phaser.awaitAdvanceInterruptibly(phase);
    }

    private class PhaserPartyTicket implements FlushTicket {

        public PhaserPartyTicket() {
            phaser.register();
        }

        @Override
        public void close() {
            phaser.arrive();
        }
    }
}
