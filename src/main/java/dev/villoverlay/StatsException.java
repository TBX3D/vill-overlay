package dev.villoverlay;

/** A stats fetch failed. {@code throttled} = transient (rate limit / 5xx), worth retrying soon. */
public final class StatsException extends Exception {
    public final boolean throttled;

    public StatsException(String message, boolean throttled) {
        super(message);
        this.throttled = throttled;
    }
}
