package kata.rt;

/** Paints a top-to-bottom color gradient behind its child. */
public final class Gradient implements Ui {
    public final Color from;
    public final Color to;
    public final Ui child;

    public Gradient(Color from, Color to, Ui child) {
        this.from = from;
        this.to = to;
        this.child = child;
    }
}
