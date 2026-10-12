package kata.rt;

/** Runs an action when the finger is flicked left or right across its child. */
public final class Swipeable implements Ui {
    public final Action onSwipeLeft;
    public final Action onSwipeRight;
    public final Ui child;

    public Swipeable(Action onSwipeLeft, Action onSwipeRight, Ui child) {
        this.onSwipeLeft = onSwipeLeft;
        this.onSwipeRight = onSwipeRight;
        this.child = child;
    }
}
