package kata.rt;

/** Text with full control: size (sp), color, weight, slant and a line limit (longer text ends with an ellipsis). */
public final class Label implements Ui {
    public final String text;
    public final long size;
    public final Color color;
    public final boolean bold;
    public final boolean italic;
    public final long maxLines;

    public Label(String text, long size, Color color, boolean bold, boolean italic, long maxLines) {
        this.text = text;
        this.size = size;
        this.color = color;
        this.bold = bold;
        this.italic = italic;
        this.maxLines = maxLines;
    }
}
