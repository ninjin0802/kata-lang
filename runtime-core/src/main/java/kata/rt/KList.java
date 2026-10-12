package kata.rt;

/** Immutable list. Int elements are stored as Long, Bool as Boolean. */
public final class KList {
    private final Object[] items;

    private KList(Object[] items) {
        this.items = items;
    }

    public static KList of(Object[] items) {
        return new KList(items.clone());
    }

    public long size() {
        return items.length;
    }

    public Object get(long index) {
        if (index < 0 || index >= items.length) {
            throw new IndexOutOfBoundsException("index " + index + " out of range for list of size " + items.length);
        }
        return items[(int) index];
    }

    public KList append(Object item) {
        Object[] next = new Object[items.length + 1];
        System.arraycopy(items, 0, next, 0, items.length);
        next[items.length] = item;
        return new KList(next);
    }
}
