package kata.rt;

import static kata.rt.Layout.Dim.FILL;
import static kata.rt.Layout.Dim.WRAP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kata.rt.Layout.Dim;
import kata.rt.Layout.Gravity;
import kata.rt.Layout.Parent;
import kata.rt.Layout.Spec;
import org.junit.jupiter.api.Test;

class LayoutTest {
    private static final Ui LEAF = new Text("x");
    private static final Color BLACK = new Color(0, 0, 0, 255);

    private static void assertSpec(Spec spec, Dim width, Dim height, Dim childWidth, Dim childHeight,
            Gravity gravity) {
        assertEquals(width, spec.width, "width");
        assertEquals(height, spec.height, "height");
        assertEquals(childWidth, spec.childWidth, "childWidth");
        assertEquals(childHeight, spec.childHeight, "childHeight");
        assertEquals(gravity, spec.childGravity, "gravity");
        assertFalse(spec.weighted);
    }

    private static Spec other(Ui node, Dim iw, Dim ih) {
        return Layout.spec(node, Parent.OTHER, iw, ih);
    }

    @Test
    void widthAndHeightFixOneAxis() {
        assertSpec(other(new Width(120, LEAF), WRAP, WRAP), Dim.fixed(120), WRAP, FILL, WRAP, Gravity.TOP_START);
        assertSpec(other(new Width(120, LEAF), WRAP, FILL), Dim.fixed(120), FILL, FILL, FILL, Gravity.TOP_START);
        assertSpec(other(new Height(48, LEAF), WRAP, WRAP), WRAP, Dim.fixed(48), WRAP, FILL, Gravity.TOP_START);
        assertSpec(other(new Height(48, LEAF), Dim.fixed(10), WRAP), Dim.fixed(10), Dim.fixed(48), FILL, FILL,
                Gravity.TOP_START);
    }

    @Test
    void fillStretchesOneAxis() {
        assertSpec(other(new FillWidth(LEAF), WRAP, WRAP), FILL, WRAP, FILL, WRAP, Gravity.TOP_START);
        assertSpec(other(new FillHeight(LEAF), WRAP, WRAP), WRAP, FILL, WRAP, FILL, Gravity.TOP_START);
        assertSpec(other(new FillHeight(LEAF), FILL, WRAP), FILL, FILL, FILL, FILL, Gravity.TOP_START);
    }

    @Test
    void alignmentWrappersFillAndPositionTheirChild() {
        assertSpec(other(new Center(LEAF), WRAP, WRAP), FILL, FILL, WRAP, WRAP, Gravity.CENTER);
        assertSpec(other(new CenterHorizontal(LEAF), WRAP, WRAP), FILL, WRAP, WRAP, WRAP,
                Gravity.CENTER_HORIZONTAL);
        assertSpec(other(new CenterHorizontal(LEAF), WRAP, FILL), FILL, FILL, WRAP, FILL,
                Gravity.CENTER_HORIZONTAL);
        assertSpec(other(new CenterVertical(LEAF), WRAP, WRAP), WRAP, FILL, WRAP, WRAP, Gravity.CENTER_VERTICAL);
        assertSpec(other(new AlignEnd(LEAF), WRAP, WRAP), FILL, WRAP, WRAP, WRAP, Gravity.END);
        assertSpec(other(new AlignBottom(LEAF), FILL, WRAP), FILL, FILL, FILL, WRAP, Gravity.BOTTOM);
    }

    @Test
    void decorationsPassSizesThrough() {
        Ui[] decorations = {new Padding(4, LEAF), new PaddingEdges(1, 2, 3, 4, LEAF), new Background(BLACK, LEAF),
            new Scroll(LEAF), new HorizontalScroll(LEAF)};
        for (Ui node : decorations) {
            assertSpec(other(node, WRAP, WRAP), WRAP, WRAP, WRAP, WRAP, Gravity.TOP_START);
            assertSpec(other(node, FILL, Dim.fixed(48)), FILL, Dim.fixed(48), FILL, FILL, Gravity.TOP_START);
        }
    }

    @Test
    void expandTakesTheRemainderAlongTheParentAxis() {
        Spec inColumn = Layout.spec(new Expand(LEAF), Parent.COLUMN, WRAP, WRAP);
        assertTrue(inColumn.weighted);
        assertEquals(WRAP, inColumn.width);
        assertEquals(Dim.fixed(0), inColumn.height);
        assertEquals(WRAP, inColumn.childWidth);
        assertEquals(FILL, inColumn.childHeight);

        Spec inRow = Layout.spec(new Expand(LEAF), Parent.ROW, WRAP, WRAP);
        assertTrue(inRow.weighted);
        assertEquals(Dim.fixed(0), inRow.width);
        assertEquals(WRAP, inRow.height);
        assertEquals(FILL, inRow.childWidth);
        assertEquals(WRAP, inRow.childHeight);

        assertSpec(other(new Expand(LEAF), FILL, WRAP), FILL, WRAP, FILL, WRAP, Gravity.TOP_START);
    }

    @Test
    void spaceIsAFixedSquare() {
        Spec spec = other(new Space(8), FILL, FILL);
        assertEquals(Dim.fixed(8), spec.width);
        assertEquals(Dim.fixed(8), spec.height);
    }

    @Test
    void containersAndLeavesTakeTheInheritedSize() {
        Ui column = new Column(KList.of(new Object[] {LEAF}));
        assertSpec(other(column, FILL, WRAP), FILL, WRAP, WRAP, WRAP, Gravity.TOP_START);
        Ui row = new Row(KList.of(new Object[] {LEAF}));
        assertSpec(other(row, WRAP, FILL), WRAP, FILL, WRAP, WRAP, Gravity.TOP_START);
        Spec leaf = other(LEAF, FILL, Dim.fixed(3));
        assertEquals(FILL, leaf.width);
        assertEquals(Dim.fixed(3), leaf.height);
    }

    @Test
    void nestedSizingReachesTheInnermostChild() {
        Spec width = other(new Width(120, new Height(48, LEAF)), WRAP, WRAP);
        Spec height = other(new Height(48, LEAF), width.childWidth, width.childHeight);
        assertEquals(FILL, height.width);
        assertEquals(Dim.fixed(48), height.height);
        Spec leaf = other(LEAF, height.childWidth, height.childHeight);
        assertEquals(FILL, leaf.width);
        assertEquals(FILL, leaf.height);
    }

    @Test
    void fillWidthPassesThroughPadding() {
        Spec fill = other(new FillWidth(new Padding(4, LEAF)), WRAP, WRAP);
        Spec padding = other(new Padding(4, LEAF), fill.childWidth, fill.childHeight);
        Spec leaf = other(LEAF, padding.childWidth, padding.childHeight);
        assertEquals(FILL, leaf.width);
        assertEquals(WRAP, leaf.height);
    }

    @Test
    void parentKindFollowsTheContainer() {
        assertEquals(Parent.COLUMN, Layout.parentKind(new Column(KList.of(new Object[0]))));
        assertEquals(Parent.ROW, Layout.parentKind(new Row(KList.of(new Object[0]))));
        assertEquals(Parent.OTHER, Layout.parentKind(new Padding(1, LEAF)));
    }
}
