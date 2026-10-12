package kata.rt;

/** A title bar with a back arrow. */
public final class BackBar implements Ui {
    public final String title;
    public final Action onBack;

    public BackBar(String title, Action onBack) {
        this.title = title;
        this.onBack = onBack;
    }
}
