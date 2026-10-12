package kata.rt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Structural access to Ui values, shared by the validator, the reconciler and the renderer. */
public final class UiNodes {
    private UiNodes() {}

    /** The child components of a node: all children of a Column or Row, the single child of a wrapper. */
    public static List<Ui> children(Ui node) {
        if (node instanceof Column) {
            return toList(((Column) node).children);
        }
        if (node instanceof Row) {
            return toList(((Row) node).children);
        }
        if (node instanceof Stack) {
            return toList(((Stack) node).children);
        }
        if (node instanceof Grid) {
            return toList(((Grid) node).children);
        }
        Ui child = child(node);
        return child == null ? Collections.<Ui>emptyList() : Collections.singletonList(child);
    }

    /** The single child of a wrapper component, or null. */
    public static Ui child(Ui node) {
        if (node instanceof Scroll) {
            return ((Scroll) node).child;
        }
        if (node instanceof HorizontalScroll) {
            return ((HorizontalScroll) node).child;
        }
        if (node instanceof Padding) {
            return ((Padding) node).child;
        }
        if (node instanceof PaddingEdges) {
            return ((PaddingEdges) node).child;
        }
        if (node instanceof Background) {
            return ((Background) node).child;
        }
        if (node instanceof Width) {
            return ((Width) node).child;
        }
        if (node instanceof Height) {
            return ((Height) node).child;
        }
        if (node instanceof FillWidth) {
            return ((FillWidth) node).child;
        }
        if (node instanceof FillHeight) {
            return ((FillHeight) node).child;
        }
        if (node instanceof Expand) {
            return ((Expand) node).child;
        }
        if (node instanceof Center) {
            return ((Center) node).child;
        }
        if (node instanceof CenterHorizontal) {
            return ((CenterHorizontal) node).child;
        }
        if (node instanceof CenterVertical) {
            return ((CenterVertical) node).child;
        }
        if (node instanceof AlignEnd) {
            return ((AlignEnd) node).child;
        }
        if (node instanceof AlignBottom) {
            return ((AlignBottom) node).child;
        }
        if (node instanceof Tappable) {
            return ((Tappable) node).child;
        }
        if (node instanceof Rounded) {
            return ((Rounded) node).child;
        }
        if (node instanceof Border) {
            return ((Border) node).child;
        }
        if (node instanceof Card) {
            return ((Card) node).child;
        }
        return null;
    }

    private static List<Ui> toList(KList list) {
        List<Ui> result = new ArrayList<Ui>((int) list.size());
        for (long i = 0; i < list.size(); i++) {
            result.add((Ui) list.get(i));
        }
        return result;
    }
}
