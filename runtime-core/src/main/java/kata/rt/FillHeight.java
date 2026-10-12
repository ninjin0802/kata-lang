package kata.rt;

/** Stretches its child to the full height offered by the parent. */
public final class FillHeight implements Ui {
    public final Ui child;

    public FillHeight(Ui child) {
        this.child = child;
    }
}
