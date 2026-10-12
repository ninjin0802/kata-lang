package kata.rt;

/** A horizontal slider from 0 to max. The value lives in the Screen. */
public final class Slider implements Ui {
    public final long value;
    public final long max;
    public final Handler onChange;

    public Slider(long value, long max, Handler onChange) {
        this.value = value;
        this.max = max;
        this.onChange = onChange;
    }
}
