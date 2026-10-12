package kata.rt;

/** Draws its child partly transparent: 100 is opaque, 0 is invisible. */
public final class Opacity implements Ui {
    public final long percent;
    public final Ui child;

    public Opacity(long percent, Ui child) {
        this.percent = percent;
        this.child = child;
    }
}
