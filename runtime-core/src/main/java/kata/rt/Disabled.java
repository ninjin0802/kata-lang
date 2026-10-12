package kata.rt;

/** Shows its child dimmed and ignores every touch on it. */
public final class Disabled implements Ui {
    public final Ui child;

    public Disabled(Ui child) {
        this.child = child;
    }
}
