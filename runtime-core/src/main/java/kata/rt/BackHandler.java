package kata.rt;

/** While shown, the system back gesture runs onBack instead of closing the app. */
public final class BackHandler implements Ui {
    public final Action onBack;
    public final Ui child;

    public BackHandler(Action onBack, Ui child) {
        this.onBack = onBack;
        this.child = child;
    }
}
