package kata.rt;

/** A button with explicit text and background colors. */
public final class StyledButton implements Ui {
    public final String label;
    public final Action onTap;
    public final Color textColor;
    public final Color background;

    public StyledButton(String label, Action onTap, Color textColor, Color background) {
        this.label = label;
        this.onTap = onTap;
        this.textColor = textColor;
        this.background = background;
    }
}
