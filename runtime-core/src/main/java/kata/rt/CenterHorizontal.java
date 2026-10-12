package kata.rt;

/** Fills the offered width and centers its child horizontally. */
public final class CenterHorizontal implements Ui {
    public final Ui child;

    public CenterHorizontal(Ui child) {
        this.child = child;
    }
}
