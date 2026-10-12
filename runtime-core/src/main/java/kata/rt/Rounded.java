package kata.rt;

/** Paints a color with rounded corners (dp) behind its child and clips the child to that shape. */
public final class Rounded implements Ui {
    public final long radius;
    public final Color color;
    public final Ui child;

    public Rounded(long radius, Color color, Ui child) {
        this.radius = radius;
        this.color = color;
        this.child = child;
    }
}
