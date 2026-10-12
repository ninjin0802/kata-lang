package kata.rt;

/** A row of equal-width tabs; selected is the index of the active one. */
public final class Tabs implements Ui {
    public final KList labels;
    public final long selected;
    public final Handler onChange;

    public Tabs(KList labels, long selected, Handler onChange) {
        this.labels = labels;
        this.selected = selected;
        this.onChange = onChange;
    }
}
