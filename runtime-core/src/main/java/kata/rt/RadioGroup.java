package kata.rt;

/** A vertical group of radio buttons. selected is the index of the chosen option, or -1 for none. */
public final class RadioGroup implements Ui {
    public final KList options;
    public final long selected;
    public final Handler onChange;

    public RadioGroup(KList options, long selected, Handler onChange) {
        this.options = options;
        this.selected = selected;
        this.onChange = onChange;
    }
}
