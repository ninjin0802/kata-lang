package kata.rt;

/** A time of day. hour is 0 to 23. */
public final class Time {
    public final long hour;
    public final long minute;

    public Time(long hour, long minute) {
        this.hour = hour;
        this.minute = minute;
    }
}
