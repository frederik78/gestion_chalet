package ca.gestionchalet.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Horloge qu'un test avance à volonté : un délai se franchit sans attente réelle. */
public final class TestClock extends Clock {

    private volatile Instant maintenant = Instant.parse("2026-11-01T12:00:00Z");

    public void avancer(Duration duree) {
        maintenant = maintenant.plus(duree);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    /** Une vue dans ce fuseau, qui suit cette horloge : la date du jour y est celle du fuseau demandé. */
    @Override
    public Clock withZone(ZoneId zone) {
        TestClock source = this;
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return zone;
            }

            @Override
            public Clock withZone(ZoneId autre) {
                return source.withZone(autre);
            }

            @Override
            public Instant instant() {
                return source.instant();
            }
        };
    }

    @Override
    public Instant instant() {
        return maintenant;
    }
}
