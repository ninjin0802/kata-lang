package kata.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReconcilerTest {
    /** A stand-in for a View that remembers its children. */
    private static final class FakeView {
        final String kind;
        final List<FakeView> children = new ArrayList<>();
        String text = "";

        FakeView(String kind) {
            this.kind = kind;
        }
    }

    /** Records every call so that tests can assert exactly what was touched. */
    private static final class FakeHost implements Host<FakeView> {
        final List<String> log = new ArrayList<>();

        @Override
        public FakeView create(Ui node) {
            FakeView view = new FakeView(node.getClass().getSimpleName());
            view.text = textOf(node);
            log.add("create " + view.kind);
            return view;
        }

        @Override
        public void update(FakeView view, Ui oldNode, Ui newNode) {
            view.text = textOf(newNode);
            log.add("update " + view.kind);
        }

        @Override
        public void insertChild(FakeView parent, int index, FakeView child) {
            parent.children.add(index, child);
            log.add("insert " + parent.kind + "[" + index + "] " + child.kind);
        }

        @Override
        public void removeChild(FakeView parent, int index) {
            FakeView removed = parent.children.remove(index);
            log.add("remove " + parent.kind + "[" + index + "] " + removed.kind);
        }

        @Override
        public void replaceChild(FakeView parent, int index, FakeView child) {
            parent.children.set(index, child);
            log.add("replace " + parent.kind + "[" + index + "] " + child.kind);
        }

        @Override
        public FakeView childAt(FakeView parent, int index) {
            return parent.children.get(index);
        }

        long count(String prefix) {
            return log.stream().filter(entry -> entry.startsWith(prefix)).count();
        }

        private static String textOf(Ui node) {
            return node instanceof Text ? ((Text) node).text : node instanceof Button ? ((Button) node).label : "";
        }
    }

    private final FakeHost host = new FakeHost();
    private final Reconciler<FakeView> reconciler = new Reconciler<>(host);

    private static Column column(Ui... children) {
        return new Column(KList.of(children));
    }

    private static Button button(String label) {
        return new Button(label, () -> { });
    }

    @Test
    void mountCreatesTheTreeTopDownInOrder() {
        FakeView root = reconciler.mount(column(new Text("a"), button("b")));
        assertEquals(List.of("create Column", "create Text", "insert Column[0] Text", "create Button",
                "insert Column[1] Button"), host.log);
        assertEquals("a", root.children.get(0).text);
    }

    @Test
    void sameShapeUpdatesInPlace() {
        Ui before = column(new Text("0"), button("+1"));
        FakeView root = reconciler.mount(before);
        FakeView text = root.children.get(0);
        host.log.clear();

        FakeView after = reconciler.patch(root, before, column(new Text("1"), button("+1")));

        assertSame(root, after);
        assertSame(text, root.children.get(0));
        assertEquals("1", text.text);
        assertEquals(List.of("update Column", "update Text", "update Button"), host.log);
    }

    @Test
    void addedChildrenAreAppended() {
        Ui before = column(new Text("a"), new Text("b"));
        FakeView root = reconciler.mount(before);
        host.log.clear();

        reconciler.patch(root, before, column(new Text("a"), new Text("b"), button("c")));

        assertEquals(List.of("update Column", "update Text", "update Text", "create Button",
                "insert Column[2] Button"), host.log);
        assertEquals(3, root.children.size());
    }

    @Test
    void removedChildrenAreDroppedFromTheEnd() {
        Ui before = column(new Text("a"), new TextField("v", "h", value -> { }), button("c"));
        FakeView root = reconciler.mount(before);
        FakeView first = root.children.get(0);
        host.log.clear();

        reconciler.patch(root, before, column(new Text("a")));

        assertEquals(List.of("update Column", "update Text", "remove Column[2] Button",
                "remove Column[1] TextField"), host.log);
        assertEquals(List.of(first), root.children);
    }

    @Test
    void removingEveryChildLeavesAnEmptyContainer() {
        Ui before = column(new Text("a"), new Text("b"));
        FakeView root = reconciler.mount(before);
        reconciler.patch(root, before, column());
        assertEquals(0, root.children.size());
    }

    @Test
    void changedKindReplacesOnlyThatPosition() {
        Ui before = column(new Text("a"), new Text("b"), new Text("c"));
        FakeView root = reconciler.mount(before);
        FakeView first = root.children.get(0);
        FakeView second = root.children.get(1);
        host.log.clear();

        reconciler.patch(root, before, column(new Text("a"), button("b"), new Text("c")));

        assertSame(first, root.children.get(0));
        assertNotSame(second, root.children.get(1));
        assertEquals("Button", root.children.get(1).kind);
        assertEquals(1, host.count("create"));
        assertEquals(1, host.count("replace"));
        assertEquals(3, host.count("update"));
    }

    @Test
    void changedRootKindReturnsANewView() {
        Ui before = column(new Text("a"));
        FakeView root = reconciler.mount(before);
        FakeView after = reconciler.patch(root, before, new Scroll(new Text("a")));
        assertNotSame(root, after);
        assertEquals("Scroll", after.kind);
        assertEquals("Text", after.children.get(0).kind);
    }

    @Test
    void wrapperChildKindChangeReplacesIndexZero() {
        Ui before = new Padding(4, new Text("a"));
        FakeView root = reconciler.mount(before);
        host.log.clear();

        FakeView after = reconciler.patch(root, before, new Padding(4, button("a")));

        assertSame(root, after);
        assertEquals(List.of("update Padding", "create Button", "replace Padding[0] Button"), host.log);
    }

    @Test
    void replacedSubtreeIsBuiltCompletely() {
        Ui before = column(new Text("a"));
        FakeView root = reconciler.mount(before);
        reconciler.patch(root, before, column(new Padding(1, column(new Text("x"), new Text("y")))));
        FakeView padding = root.children.get(0);
        assertEquals("Padding", padding.kind);
        assertEquals(2, padding.children.get(0).children.size());
    }

    @Test
    void deepChangeCreatesNothing() {
        Ui before = new Scroll(new Padding(1, column(new Center(new Background(new Color(0, 0, 0, 255),
                new Text("deep"))))));
        Ui next = new Scroll(new Padding(1, column(new Center(new Background(new Color(0, 0, 0, 255),
                new Text("changed"))))));
        FakeView root = reconciler.mount(before);
        host.log.clear();

        reconciler.patch(root, before, next);

        assertEquals(0, host.count("create"));
        assertEquals(6, host.count("update"));
        FakeView text = root.children.get(0).children.get(0).children.get(0).children.get(0).children.get(0);
        assertEquals("changed", text.text);
    }
}
