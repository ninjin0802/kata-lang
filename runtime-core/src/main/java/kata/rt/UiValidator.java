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
        } else if (root instanceof RadioGroup) {
            RadioGroup group = (RadioGroup) root;
            if (group.selected < -1 || group.selected >= group.options.size()) {
                throw new IllegalArgumentException("RadioGroup.selected out of range: " + group.selected);
            }
        } else if (root instanceof Dropdown) {
            Dropdown dropdown = (Dropdown) root;
            if (dropdown.options.size() < 1) {
                throw new IllegalArgumentException("Dropdown.options out of range: " + dropdown.options.size());
            }
            if (dropdown.selected < 0 || dropdown.selected >= dropdown.options.size()) {
                throw new IllegalArgumentException("Dropdown.selected out of range: " + dropdown.selected);
            }
        } else if (root instanceof Rating) {
            range("Rating", ((Rating) root).value, ((Rating) root).max);
        } else if (root instanceof Grid) {
            if (((Grid) root).columns < 1) {
                throw new IllegalArgumentException("Grid.columns out of range: " + ((Grid) root).columns);
            }
        } else if (root instanceof Border) {
            nonNegative("Border.width", ((Border) root).width);
            nonNegative("Border.radius", ((Border) root).radius);
            color(((Border) root).color);
        } else if (root instanceof Label) {
            Label label = (Label) root;
            nonNegative("Label.size", label.size);
            if (label.maxLines < 1) {
                throw new IllegalArgumentException("Label.maxLines out of range: " + label.maxLines);
            }
            color(label.color);
        } else if (root instanceof Tabs) {
            index("Tabs", ((Tabs) root).labels.size(), ((Tabs) root).selected);
        } else if (root instanceof BottomNav) {
            index("BottomNav", ((BottomNav) root).labels.size(), ((BottomNav) root).selected);
        } else if (root instanceof Badge) {
            nonNegative("Badge.count", ((Badge) root).count);
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

    /** There must be at least one label and selected must be one of them. */
    private static void index(String component, long size, long selected) {
        if (size < 1) {
            throw new IllegalArgumentException(component + ".labels out of range: " + size);
        }
        if (selected < 0 || selected >= size) {
            throw new IllegalArgumentException(component + ".selected out of range: " + selected);
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
