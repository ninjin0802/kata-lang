package kata.rt;

/** Fixes the height (dp) of its child. */
public final class Height implements Ui {
    public final long dp;
    public final Ui child;

    public Height(long dp, Ui child) {
        this.dp = dp;
        this.child = child;
    }
}
