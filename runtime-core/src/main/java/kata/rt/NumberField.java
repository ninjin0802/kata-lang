package kata.rt;

/** A single-line text input with a numeric keyboard. The value is still text. */
public final class NumberField implements Ui {
    public final String value;
    public final String hint;
    public final Handler onChange;

    public NumberField(String value, String hint, Handler onChange) {
        this.value = value;
        this.hint = hint;
        this.onChange = onChange;
    }
}
