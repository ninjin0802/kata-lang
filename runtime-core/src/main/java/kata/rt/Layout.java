package kata.rt;

/** Decides the size and placement of every component (spec 05 #layout). Independent of Android. */
public final class Layout {
    private Layout() {}

    /** The kind of the direct parent, which matters only to Expand. */
    public enum Parent { COLUMN, ROW, OTHER }

    /** Where a wrapper places its single child. */
    public enum Gravity { TOP_START, CENTER, CENTER_HORIZONTAL, CENTER_VERTICAL, END, BOTTOM }

    /** A size along one axis: fit the content, fill what the parent offers, or a fixed number of dp. */
    public static final class Dim {
        public static final Dim WRAP = new Dim(-1);
        public static final Dim FILL = new Dim(-2);

        private final long value;

        private Dim(long value) {
            this.value = value;
        }

        public static Dim fixed(long dp) {
            return new Dim(dp);
        }

        public boolean isWrap() {
            return value == -1;
        }

        public boolean isFill() {
            return value == -2;
        }

        public boolean isFixed() {
            return value >= 0;
        }

        /** The size in dp; only for a fixed size. */
        public long dp() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Dim && ((Dim) other).value == value;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(value);
        }

        @Override
        public String toString() {
            return isWrap() ? "WRAP" : isFill() ? "FILL" : value + "dp";
        }
    }

    /** The layout of one component and what it passes down to its children. */
    public static final class Spec {
        public final Dim width;
        public final Dim height;
        /** True for an Expand directly inside a Column or Row: it shares the remaining length. */
        public final boolean weighted;
        public final Gravity childGravity;
        public final Dim childWidth;
        public final Dim childHeight;

        Spec(Dim width, Dim height, boolean weighted, Gravity childGravity, Dim childWidth, Dim childHeight) {
            this.width = width;
            this.height = height;
            this.weighted = weighted;
            this.childGravity = childGravity;
            this.childWidth = childWidth;
            this.childHeight = childHeight;
        }
    }

    /** What the children of this node see as their parent. */
    public static Parent parentKind(Ui node) {
        if (node instanceof Column) {
            return Parent.COLUMN;
        }
        if (node instanceof Row) {
            return Parent.ROW;
        }
        return Parent.OTHER;
    }

    /**
     * @param inheritedWidth the width the parent passes down (WRAP for the root and for children of Column/Row)
     * @param inheritedHeight the height the parent passes down
     */
    public static Spec spec(Ui node, Parent parent, Dim inheritedWidth, Dim inheritedHeight) {
        // A component that would fit its content still fills when something inside it asks to fill.
        Dim iw = inheritedWidth.isWrap() && fills(node, true) ? Dim.FILL : inheritedWidth;
        Dim ih = inheritedHeight.isWrap() && fills(node, false) ? Dim.FILL : inheritedHeight;
        if (node instanceof Width) {
            return spec(Dim.fixed(((Width) node).dp), ih, Dim.FILL, pass(ih), Gravity.TOP_START);
        }
        if (node instanceof Height) {
            return spec(iw, Dim.fixed(((Height) node).dp), pass(iw), Dim.FILL, Gravity.TOP_START);
        }
        if (node instanceof FillWidth) {
            return spec(Dim.FILL, ih, Dim.FILL, pass(ih), Gravity.TOP_START);
        }
        if (node instanceof FillHeight) {
            return spec(iw, Dim.FILL, pass(iw), Dim.FILL, Gravity.TOP_START);
        }
        if (node instanceof Center) {
            return spec(Dim.FILL, Dim.FILL, Dim.WRAP, Dim.WRAP, Gravity.CENTER);
        }
        if (node instanceof CenterHorizontal) {
            return spec(Dim.FILL, ih, Dim.WRAP, pass(ih), Gravity.CENTER_HORIZONTAL);
        }
        if (node instanceof CenterVertical) {
            return spec(iw, Dim.FILL, pass(iw), Dim.WRAP, Gravity.CENTER_VERTICAL);
        }
        if (node instanceof AlignEnd) {
            return spec(Dim.FILL, ih, Dim.WRAP, pass(ih), Gravity.END);
        }
        if (node instanceof AlignBottom) {
            return spec(iw, Dim.FILL, pass(iw), Dim.WRAP, Gravity.BOTTOM);
        }
        if (node instanceof Expand) {
            if (parent == Parent.COLUMN) {
                return new Spec(iw, Dim.fixed(0), true, Gravity.TOP_START, pass(iw), Dim.FILL);
            }
            if (parent == Parent.ROW) {
                return new Spec(Dim.fixed(0), ih, true, Gravity.TOP_START, Dim.FILL, pass(ih));
            }
            return spec(iw, ih, pass(iw), pass(ih), Gravity.TOP_START);
        }
        if (node instanceof Space) {
            Dim size = Dim.fixed(((Space) node).dp);
            return spec(size, size, Dim.WRAP, Dim.WRAP, Gravity.TOP_START);
        }
        if (node instanceof Column || node instanceof Row) {
            return spec(iw, ih, Dim.WRAP, Dim.WRAP, Gravity.TOP_START);
        }
        // Padding, PaddingEdges, Background, Scroll, HorizontalScroll and leaves.
        return spec(iw, ih, pass(iw), pass(ih), Gravity.TOP_START);
    }

    /**
     * Whether the node asks for the full width (or height) offered to it, either itself or through a
     * descendant. A fixed size and the scroll axis of a scroll container stop the request.
     */
    static boolean fills(Ui node, boolean widthAxis) {
        if (widthAxis
                ? node instanceof FillWidth || node instanceof CenterHorizontal || node instanceof AlignEnd
                : node instanceof FillHeight || node instanceof CenterVertical || node instanceof AlignBottom) {
            return true;
        }
        if (node instanceof Center) {
            return true;
        }
        if (node instanceof Space || node instanceof Width && widthAxis || node instanceof Height && !widthAxis
                || node instanceof HorizontalScroll && widthAxis || node instanceof Scroll && !widthAxis) {
            return false;
        }
        boolean mainAxis = node instanceof Row && widthAxis || node instanceof Column && !widthAxis;
        for (Ui child : UiNodes.children(node)) {
            if (mainAxis && child instanceof Expand || fills(child, widthAxis)) {
                return true;
            }
        }
        return false;
    }

    private static Spec spec(Dim width, Dim height, Dim childWidth, Dim childHeight, Gravity gravity) {
        return new Spec(width, height, false, gravity, childWidth, childHeight);
    }

    /** A child fills an axis that its parent has determined; otherwise it fits its content. */
    private static Dim pass(Dim dim) {
        return dim.isWrap() ? Dim.WRAP : Dim.FILL;
    }
}
