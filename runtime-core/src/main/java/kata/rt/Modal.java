package kata.rt;

/** Shows its child in a centered dialog for as long as it is part of the view. */
public final class Modal implements Ui {
    public final Ui child;
    public final Action onDismiss;

    public Modal(Ui child, Action onDismiss) {
        this.child = child;
        this.onDismiss = onDismiss;
    }
}
