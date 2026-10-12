package kata.rt;

/** A single-line text input. The text lives in the Screen; see spec 05 #text-field. */
public final class TextField implements Ui {
    public final String value;
    public final String hint;
    public final Handler onChange;

    public TextField(String value, String hint, Handler onChange) {
        this.value = value;
        this.hint = hint;
        this.onChange = onChange;
    }
}
