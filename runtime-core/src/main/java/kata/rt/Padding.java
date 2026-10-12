package kata.rt;

/** Space (dp) around all four sides of its child. */
public final class Padding implements Ui {
    public final long all;
    public final Ui child;

    public Padding(long all, Ui child) {
        this.all = all;
        this.child = child;
    }
}
