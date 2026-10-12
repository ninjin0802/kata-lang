package kata.rt;

/** Fills the offered area and centers its child in both directions. */
public final class Center implements Ui {
    public final Ui child;

    public Center(Ui child) {
        this.child = child;
    }
}
