package kata.rt;

/** A drop-down menu showing the selected option. selected is an index into options. */
public final class Dropdown implements Ui {
    public final KList options;
    public final long selected;
    public final Handler onChange;

    public Dropdown(KList options, long selected, Handler onChange) {
        this.options = options;
        this.selected = selected;
        this.onChange = onChange;
    }
}
