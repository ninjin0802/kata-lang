package kata.rt;

/** Text with an explicit size (sp) and color. */
public final class StyledText implements Ui {
    public final String text;
    public final long size;
    public final Color color;

    public StyledText(String text, long size, Color color) {
        this.text = text;
        this.size = size;
        this.color = color;
    }
}
