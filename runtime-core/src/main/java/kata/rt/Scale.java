package kata.rt;

/** Draws its child larger or smaller around its center: 100 is the normal size. The space it takes does not change. */
public final class Scale implements Ui {
    public final long percent;
    public final Ui child;

    public Scale(long percent, Ui child) {
        this.percent = percent;
        this.child = child;
    }
}
