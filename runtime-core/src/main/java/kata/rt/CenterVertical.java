package kata.rt;

/** Fills the offered height and centers its child vertically. */
public final class CenterVertical implements Ui {
    public final Ui child;

    public CenterVertical(Ui child) {
        this.child = child;
    }
}
