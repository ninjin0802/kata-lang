package kata.rt;

/** Fills the offered width and places its child at the right edge. */
public final class AlignEnd implements Ui {
    public final Ui child;

    public AlignEnd(Ui child) {
        this.child = child;
    }
}
