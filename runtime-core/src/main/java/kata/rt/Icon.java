package kata.rt;

/** A single-color picture from assets, drawn as a square of the given size (dp) in the given color. */
public final class Icon implements Ui {
    public final String name;
    public final long size;
    public final Color color;

    public Icon(String name, long size, Color color) {
        this.name = name;
        this.size = size;
        this.color = color;
    }
}
