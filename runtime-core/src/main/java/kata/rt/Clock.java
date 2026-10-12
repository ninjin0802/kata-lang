package kata.rt;

import java.util.Calendar;

/** The current date and time of the device. */
public final class Clock {
    public Clock() {}

    /** Milliseconds since 1970-01-01 00:00 UTC. */
    public long now() {
        return System.currentTimeMillis();
    }

    /** Today's date in the device's time zone. */
    public Date today() {
        Calendar calendar = Calendar.getInstance();
        return new Date(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    /** The current time of day in the device's time zone. */
    public Time time() {
        Calendar calendar = Calendar.getInstance();
        return new Time(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
    }
}
