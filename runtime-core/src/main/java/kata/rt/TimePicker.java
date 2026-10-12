package kata.rt;

/** A clock for choosing a time (24-hour). */
public final class TimePicker implements Ui {
    public final Time value;
    public final Handler onChange;

    public TimePicker(Time value, Handler onChange) {
        this.value = value;
        this.onChange = onChange;
    }
}
