package kata.rt;

/** A row of max stars of which value are filled. */
public final class Rating implements Ui {
    public final long value;
    public final long max;
    public final Handler onChange;

    public Rating(long value, long max, Handler onChange) {
        this.value = value;
        this.max = max;
        this.onChange = onChange;
    }
}
