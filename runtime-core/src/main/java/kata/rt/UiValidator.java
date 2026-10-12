package kata.rt;

/** Rejects appearance values outside their range (spec 05 #styling). A violation is a panic. */
public final class UiValidator {
    private UiValidator() {}

    /** @throws IllegalArgumentException with the message `Component.field out of range: value` */
    public static void validate(Ui root) {
        if (root instanceof StyledText) {
            StyledText text = (StyledText) root;
            nonNegative("StyledText.size", text.size);
            color(text.color);
        } else if (root instanceof StyledButton) {
            StyledButton button = (StyledButton) root;
            color(button.textColor);
            color(button.background);
        } else if (root instanceof Background) {
            color(((Background) root).color);
        } else if (root instanceof Padding) {
            nonNegative("Padding.all", ((Padding) root).all);
        } else if (root instanceof PaddingEdges) {
            PaddingEdges edges = (PaddingEdges) root;
            nonNegative("PaddingEdges.left", edges.left);
            nonNegative("PaddingEdges.top", edges.top);
            nonNegative("PaddingEdges.right", edges.right);
            nonNegative("PaddingEdges.bottom", edges.bottom);
        } else if (root instanceof Width) {
            nonNegative("Width.dp", ((Width) root).dp);
        } else if (root instanceof Height) {
            nonNegative("Height.dp", ((Height) root).dp);
        } else if (root instanceof Space) {
            nonNegative("Space.dp", ((Space) root).dp);
        } else if (root instanceof Slider) {
            range("Slider", ((Slider) root).value, ((Slider) root).max);
        } else if (root instanceof Progress) {
            range("Progress", ((Progress) root).value, ((Progress) root).max);
        } else if (root instanceof TextArea) {
            if (((TextArea) root).lines < 1) {
                throw new IllegalArgumentException("TextArea.lines out of range: " + ((TextArea) root).lines);
            }
        } else if (root instanceof Rounded) {
            nonNegative("Rounded.radius", ((Rounded) root).radius);
            color(((Rounded) root).color);
        }
        for (Ui child : UiNodes.children(root)) {
            validate(child);
        }
    }

    /** max must be at least 1 and value within 0..max. */
    private static void range(String component, long value, long max) {
        if (max < 1) {
            throw new IllegalArgumentException(component + ".max out of range: " + max);
        }
        if (value < 0 || value > max) {
            throw new IllegalArgumentException(component + ".value out of range: " + value);
        }
    }

    private static void color(Color color) {
        channel("Color.red", color.red);
        channel("Color.green", color.green);
        channel("Color.blue", color.blue);
        channel("Color.alpha", color.alpha);
    }

    private static void channel(String name, long value) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " out of range: " + value);
        }
    }

    private static void nonNegative(String name, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " out of range: " + value);
        }
    }
}
