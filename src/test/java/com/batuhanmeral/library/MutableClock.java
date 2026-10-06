package com.batuhanmeral.library;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A clock a test can move forward, for rules that depend on dates passing. Clock.fixed cannot do
 * this: it is immutable, and the application holds the one instance it was given.
 */
public class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant now;

    public MutableClock(Instant now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    public void setInstant(Instant instant) {
        this.now = instant;
    }

    public void advance(Duration duration) {
        this.now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return newZone.equals(zone) ? this : new MutableClock(now, newZone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
