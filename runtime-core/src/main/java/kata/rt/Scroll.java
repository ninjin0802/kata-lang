package kata.rt;

/** Scrolls its child vertically when it does not fit. */
public final class Scroll implements Ui {
    public final Ui child;

    public Scroll(Ui child) {
        this.child = child;
    }
}
