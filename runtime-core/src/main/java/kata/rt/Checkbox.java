package kata.rt;

/** A labelled check box. The checked state lives in the Screen. */
public final class Checkbox implements Ui {
    public final String label;
    public final boolean checked;
    public final Handler onChange;

    public Checkbox(String label, boolean checked, Handler onChange) {
        this.label = label;
        this.checked = checked;
        this.onChange = onChange;
    }
}
