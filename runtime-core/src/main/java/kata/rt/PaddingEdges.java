package kata.rt;

/** Space (dp) on each side of its child. */
public final class PaddingEdges implements Ui {
    public final long left;
    public final long top;
    public final long right;
    public final long bottom;
    public final Ui child;

    public PaddingEdges(long left, long top, long right, long bottom, Ui child) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.child = child;
    }
}
