package kata.rt;

/** Fills the offered height and places its child at the bottom edge. */
public final class AlignBottom implements Ui {
    public final Ui child;

    public AlignBottom(Ui child) {
        this.child = child;
    }
}
