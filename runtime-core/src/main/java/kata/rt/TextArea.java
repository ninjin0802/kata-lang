package kata.rt;

/** A multi-line text input showing the given number of lines. */
public final class TextArea implements Ui {
    public final String value;
    public final String hint;
    public final long lines;
    public final Handler onChange;

    public TextArea(String value, String hint, long lines, Handler onChange) {
        this.value = value;
        this.hint = hint;
        this.lines = lines;
        this.onChange = onChange;
    }
}
