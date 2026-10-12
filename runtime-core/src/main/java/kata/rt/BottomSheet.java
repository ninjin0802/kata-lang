package kata.rt;

/** Shows its child in a sheet rising from the bottom for as long as it is part of the view. */
public final class BottomSheet implements Ui {
    public final Ui child;
    public final Action onDismiss;

    public BottomSheet(Ui child, Action onDismiss) {
        this.child = child;
        this.onDismiss = onDismiss;
    }
}
