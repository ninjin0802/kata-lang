package kata.rt;

/** Fixes the width (dp) of its child. */
public final class Width implements Ui {
    public final long dp;
    public final Ui child;

    public Width(long dp, Ui child) {
        this.dp = dp;
        this.child = child;
    }
}
