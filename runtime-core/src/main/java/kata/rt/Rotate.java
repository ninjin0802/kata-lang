package kata.rt;

/** Draws its child turned clockwise around its center. The space it takes does not change. */
public final class Rotate implements Ui {
    public final long degrees;
    public final Ui child;

    public Rotate(long degrees, Ui child) {
        this.degrees = degrees;
        this.child = child;
    }
}
