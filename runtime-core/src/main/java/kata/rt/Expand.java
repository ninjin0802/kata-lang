package kata.rt;

/** Takes the remaining length along the axis of the enclosing Column or Row. */
public final class Expand implements Ui {
    public final Ui child;

    public Expand(Ui child) {
        this.child = child;
    }
}
