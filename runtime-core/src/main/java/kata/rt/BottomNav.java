package kata.rt;

/** A navigation bar for the bottom of a screen; selected is the index of the active destination. */
public final class BottomNav implements Ui {
    public final KList labels;
    public final long selected;
    public final Handler onChange;

    public BottomNav(KList labels, long selected, Handler onChange) {
        this.labels = labels;
        this.selected = selected;
        this.onChange = onChange;
    }
}
