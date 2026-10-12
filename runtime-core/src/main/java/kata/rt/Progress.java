package kata.rt;

/** A horizontal progress bar showing value out of max. */
public final class Progress implements Ui {
    public final long value;
    public final long max;

    public Progress(long value, long max) {
        this.value = value;
        this.max = max;
    }
}
