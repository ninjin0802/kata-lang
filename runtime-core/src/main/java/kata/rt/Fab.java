package kata.rt;

/** A round floating action button showing a short label such as "+". */
public final class Fab implements Ui {
    public final String label;
    public final Action onTap;

    public Fab(String label, Action onTap) {
        this.label = label;
        this.onTap = onTap;
    }
}
