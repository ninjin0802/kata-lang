package kata.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class KListTest {
    @Test
    void ofKeepsItemsInOrder() {
        KList list = KList.of(new Object[] {1L, 2L});
        assertEquals(2L, list.size());
        assertEquals(2L, list.get(1));
    }

    @Test
    void appendReturnsNewListAndLeavesOriginal() {
        KList list = KList.of(new Object[] {1L, 2L});
        KList longer = list.append(3L);
        assertEquals(3L, longer.size());
        assertEquals(3L, longer.get(2));
        assertEquals(2L, list.size());
    }

    @Test
    void ofCopiesTheArray() {
        Object[] items = {"a"};
        KList list = KList.of(items);
        items[0] = "b";
        assertEquals("a", list.get(0));
    }

    @Test
    void getOutOfRangeThrows() {
        KList list = KList.of(new Object[] {1L, 2L});
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(2));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(1L << 40));
    }
}
