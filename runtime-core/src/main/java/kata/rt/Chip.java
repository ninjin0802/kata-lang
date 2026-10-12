package kata.rt;

/** A small pill that can be shown selected. */
public final class Chip implements Ui {
    public final String label;
    public final boolean selected;
    public final Action onTap;

    public Chip(String label, boolean selected, Action onTap) {
        this.label = label;
        this.selected = selected;
        this.onTap = onTap;
    }
}
