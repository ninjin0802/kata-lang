package kata.rt;

/** A color. Each channel is 0 to 255; alpha 255 is opaque. */
public final class Color {
    public final long red;
    public final long green;
    public final long blue;
    public final long alpha;

    public Color(long red, long green, long blue, long alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }
}
