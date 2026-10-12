package kata.rt;

/** Sets the accent color used by controls, tabs and floating buttons in the whole screen. */
public final class Theme implements Ui {
    public final Color accent;
    public final Ui child;

    public Theme(Color accent, Ui child) {
        this.accent = accent;
        this.child = child;
    }
}
