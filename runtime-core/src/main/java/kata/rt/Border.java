package kata.rt;

/** Draws a line of the given width (dp) around its child, with rounded corners of the given radius (dp). */
public final class Border implements Ui {
    public final long width;
    public final Color color;
    public final long radius;
    public final Ui child;

    public Border(long width, Color color, long radius, Ui child) {
        this.width = width;
        this.color = color;
        this.radius = radius;
        this.child = child;
    }
}
