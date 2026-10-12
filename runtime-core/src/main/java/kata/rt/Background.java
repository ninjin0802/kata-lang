package kata.rt;

/** Paints a color behind its child. */
public final class Background implements Ui {
    public final Color color;
    public final Ui child;

    public Background(Color color, Ui child) {
        this.color = color;
        this.child = child;
    }
}
