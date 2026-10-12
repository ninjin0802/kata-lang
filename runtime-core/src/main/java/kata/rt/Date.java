package kata.rt;

/** A calendar date. month is 1 to 12. */
public final class Date {
    public final long year;
    public final long month;
    public final long day;

    public Date(long year, long month, long day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }
}
