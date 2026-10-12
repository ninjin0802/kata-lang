package kata.rt;

/** A button drawn as an outline: for the less important action next to a filled Button. */
public final class OutlinedButton implements Ui {
    public final String label;
    public final Action onTap;

    public OutlinedButton(String label, Action onTap) {
        this.label = label;
        this.onTap = onTap;
    }
}
