package kata.rt;

public final class Button implements Ui {
    public final String label;
    public final Action onTap;

    public Button(String label, Action onTap) {
        this.label = label;
        this.onTap = onTap;
    }
}
