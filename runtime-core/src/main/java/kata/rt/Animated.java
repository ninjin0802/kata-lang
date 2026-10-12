package kata.rt;

/** Animates changes inside it (things appearing, disappearing, moving or resizing) instead of switching at once. */
public final class Animated implements Ui {
    public final Ui child;

    public Animated(Ui child) {
        this.child = child;
    }
}
