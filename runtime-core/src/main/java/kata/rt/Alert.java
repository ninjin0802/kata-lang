package kata.rt;

/** A dialog with a message and two buttons, shown for as long as it is part of the view. */
public final class Alert implements Ui {
    public final String title;
    public final String message;
    public final String confirmLabel;
    public final Action onConfirm;
    public final String cancelLabel;
    public final Action onCancel;

    public Alert(String title, String message, String confirmLabel, Action onConfirm, String cancelLabel, Action onCancel) {
        this.title = title;
        this.message = message;
        this.confirmLabel = confirmLabel;
        this.onConfirm = onConfirm;
        this.cancelLabel = cancelLabel;
        this.onCancel = onCancel;
    }
}
