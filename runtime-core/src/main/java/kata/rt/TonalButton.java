package kata.rt;

/** A button filled with the soft container color: between a filled and an outlined button in emphasis. */
public final class TonalButton implements Ui {
    public final String label;
    public final Action onTap;

    public TonalButton(String label, Action onTap) {
        this.label = label;
        this.onTap = onTap;
    }
}
