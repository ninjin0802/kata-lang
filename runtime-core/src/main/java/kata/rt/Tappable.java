package kata.rt;

/** Makes its child respond to a tap. */
public final class Tappable implements Ui {
    public final Action onTap;
    public final Ui child;

    public Tappable(Action onTap, Ui child) {
        this.onTap = onTap;
        this.child = child;
    }
}
