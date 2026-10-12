package kata.rt;

/** Scrolls its child horizontally when it does not fit. */
public final class HorizontalScroll implements Ui {
    public final Ui child;

    public HorizontalScroll(Ui child) {
        this.child = child;
    }
}
