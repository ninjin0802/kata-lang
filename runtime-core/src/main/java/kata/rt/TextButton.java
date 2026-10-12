package kata.rt;

/** A button drawn as text only: for the least important action. */
public final class TextButton implements Ui {
    public final String label;
    public final Action onTap;

    public TextButton(String label, Action onTap) {
        this.label = label;
        this.onTap = onTap;
    }
}
