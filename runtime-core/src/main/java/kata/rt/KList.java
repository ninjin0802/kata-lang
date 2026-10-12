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

    /** A new list without the element at index. */
    public KList removeAt(long index) {
        get(index);
        Object[] next = new Object[items.length - 1];
        System.arraycopy(items, 0, next, 0, (int) index);
        System.arraycopy(items, (int) index + 1, next, (int) index, items.length - (int) index - 1);
        return new KList(next);
    }

    /** A new list with the element at index replaced. */
    public KList set(long index, Object item) {
        get(index);
        Object[] next = items.clone();
        next[(int) index] = item;
        return new KList(next);
    }

    public KList append(Object item) {
        Object[] next = new Object[items.length + 1];
        System.arraycopy(items, 0, next, 0, items.length);
        next[items.length] = item;
        return new KList(next);
    }
}
