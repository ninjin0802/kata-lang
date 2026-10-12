package kata.rt;

/** Runs an action when its child is pressed and held. */
public final class LongPress implements Ui {
    public final Action onLongPress;
    public final Ui child;

    public LongPress(Action onLongPress, Ui child) {
        this.onLongPress = onLongPress;
        this.child = child;
    }
}
