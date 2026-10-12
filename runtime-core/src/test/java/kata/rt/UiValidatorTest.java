package kata.rt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UiValidatorTest {
    private static final Color BLACK = new Color(0, 0, 0, 255);
    private static final Ui LEAF = new Text("x");

    private static void assertInvalid(String message, Ui ui) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> UiValidator.validate(ui));
        assertEquals(message, e.getMessage());
    }

    @Test
    void colorChannelsMustBe0To255() {
        assertInvalid("Color.red out of range: -1", new Background(new Color(-1, 0, 0, 255), LEAF));
        assertInvalid("Color.red out of range: 256", new Background(new Color(256, 0, 0, 255), LEAF));
        assertInvalid("Color.green out of range: 300", new Background(new Color(0, 300, 0, 255), LEAF));
        assertInvalid("Color.blue out of range: -5", new Background(new Color(0, 0, -5, 255), LEAF));
        assertInvalid("Color.alpha out of range: 256", new Background(new Color(0, 0, 0, 256), LEAF));
        assertDoesNotThrow(() -> UiValidator.validate(new Background(new Color(0, 255, 0, 0), LEAF)));
    }

    @Test
    void everyColorCarrierIsChecked() {
        Color bad = new Color(0, 0, 0, 999);
        assertInvalid("Color.alpha out of range: 999", new StyledText("t", 12, bad));
        assertInvalid("Color.alpha out of range: 999", new StyledButton("b", () -> { }, bad, BLACK));
        assertInvalid("Color.alpha out of range: 999", new StyledButton("b", () -> { }, BLACK, bad));
    }

    @Test
    void sizesMustNotBeNegative() {
        assertInvalid("StyledText.size out of range: -1", new StyledText("t", -1, BLACK));
        assertInvalid("Padding.all out of range: -1", new Padding(-1, LEAF));
        assertInvalid("PaddingEdges.left out of range: -1", new PaddingEdges(-1, 0, 0, 0, LEAF));
        assertInvalid("PaddingEdges.top out of range: -1", new PaddingEdges(0, -1, 0, 0, LEAF));
        assertInvalid("PaddingEdges.right out of range: -1", new PaddingEdges(0, 0, -1, 0, LEAF));
        assertInvalid("PaddingEdges.bottom out of range: -1", new PaddingEdges(0, 0, 0, -1, LEAF));
        assertInvalid("Width.dp out of range: -1", new Width(-1, LEAF));
        assertInvalid("Height.dp out of range: -1", new Height(-1, LEAF));
        assertInvalid("Space.dp out of range: -1", new Space(-1));
    }

    @Test
    void zeroIsAllowed() {
        assertDoesNotThrow(() -> UiValidator.validate(new Column(KList.of(new Object[] {
            new StyledText("t", 0, BLACK), new Padding(0, LEAF), new PaddingEdges(0, 0, 0, 0, LEAF),
            new Width(0, LEAF), new Height(0, LEAF), new Space(0),
        }))));
    }

    @Test
    void findsViolationsDeepInTheTree() {
        Ui deep = new Scroll(new Column(KList.of(new Object[] {
            LEAF,
            new Row(KList.of(new Object[] {new Center(new Expand(new FillWidth(new Width(-7, LEAF))))})),
        })));
        assertInvalid("Width.dp out of range: -7", deep);
    }
}
