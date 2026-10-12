package kata.rt;

/** A calendar for choosing a date. */
public final class DatePicker implements Ui {
    public final Date value;
    public final Handler onChange;

    public DatePicker(Date value, Handler onChange) {
        this.value = value;
        this.onChange = onChange;
    }
}
