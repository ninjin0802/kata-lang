package kata.rt;

/** A brief message at the bottom of the screen with one action; it asks to be dismissed after a few seconds. */
public final class Snackbar implements Ui {
    public final String message;
    public final String actionLabel;
    public final Action onAction;
    public final Action onDismiss;

    public Snackbar(String message, String actionLabel, Action onAction, Action onDismiss) {
        this.message = message;
        this.actionLabel = actionLabel;
        this.onAction = onAction;
        this.onDismiss = onDismiss;
    }
}
