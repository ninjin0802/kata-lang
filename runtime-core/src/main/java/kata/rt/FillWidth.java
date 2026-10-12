package kata.rt;

/** Stretches its child to the full width offered by the parent. */
public final class FillWidth implements Ui {
    public final Ui child;

    public FillWidth(Ui child) {
        this.child = child;
    }
}
