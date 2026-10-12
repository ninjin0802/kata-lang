package kata.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

class UiNodesTest {
    private static final Color BLACK = new Color(0, 0, 0, 255);
    private static final Ui A = new Text("a");
    private static final Ui B = new Text("b");

    @Test
    void containersReturnTheirChildrenInOrder() {
        KList children = KList.of(new Object[] {A, B});
        assertEquals(List.of(A, B), UiNodes.children(new Column(children)));
        assertEquals(List.of(A, B), UiNodes.children(new Row(children)));
        assertEquals(List.of(A, B), UiNodes.children(new Stack(children)));
        assertEquals(List.of(A, B), UiNodes.children(new Grid(2, children)));
    }

    @Test
    void wrappersReturnTheirSingleChild() {
        List<Ui> wrappers = List.of(new Scroll(A), new HorizontalScroll(A), new Padding(1, A),
                new PaddingEdges(1, 2, 3, 4, A), new Background(BLACK, A), new Width(1, A), new Height(1, A),
                new FillWidth(A), new FillHeight(A), new Expand(A), new Center(A), new CenterHorizontal(A),
                new CenterVertical(A), new AlignEnd(A), new AlignBottom(A), new Tappable(() -> { }, A),
                new Rounded(4, BLACK, A), new Border(1, BLACK, 0, A), new Card(A));
        for (Ui wrapper : wrappers) {
            List<Ui> children = UiNodes.children(wrapper);
            assertEquals(1, children.size(), wrapper.getClass().getSimpleName());
            assertSame(A, children.get(0));
        }
    }

    @Test
    void leavesHaveNoChildren() {
        List<Ui> leaves = List.of(A, new Button("b", () -> { }), new TextField("v", "h", value -> { }),
                new StyledText("t", 12, BLACK), new StyledButton("b", () -> { }, BLACK, BLACK), new Space(4),
                new Checkbox("c", true, value -> { }), new Switch("s", false, value -> { }),
                new Slider(1, 10, value -> { }), new Progress(1, 10), new Loading(), new Divider(),
                new TextArea("v", "h", 3, value -> { }),
                new RadioGroup(KList.of(new Object[] {"a"}), 0, value -> { }),
                new Dropdown(KList.of(new Object[] {"a"}), 0, value -> { }), new Rating(1, 5, value -> { }));
        for (Ui leaf : leaves) {
            assertEquals(List.of(), UiNodes.children(leaf), leaf.getClass().getSimpleName());
        }
    }
}
