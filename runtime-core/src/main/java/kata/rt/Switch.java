package kata.rt;

/** A labelled on/off switch. The state lives in the Screen. */
public final class Switch implements Ui {
    public final String label;
    public final boolean on;
    public final Handler onChange;

    public Switch(String label, boolean on, Handler onChange) {
        this.label = label;
        this.on = on;
        this.onChange = onChange;
    }
}
