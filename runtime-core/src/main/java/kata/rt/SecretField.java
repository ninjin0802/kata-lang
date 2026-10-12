package kata.rt;

/** A single-line text input that hides what is typed, for passwords. */
public final class SecretField implements Ui {
    public final String value;
    public final String hint;
    public final Handler onChange;

    public SecretField(String value, String hint, Handler onChange) {
        this.value = value;
        this.hint = hint;
        this.onChange = onChange;
    }
}
