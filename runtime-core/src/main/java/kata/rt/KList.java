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

    /** The elements for which keep returns true, in order. */
    public KList filter(Fn keep) {
        java.util.ArrayList<Object> kept = new java.util.ArrayList<Object>();
        for (Object item : items) {
            if ((Boolean) keep.call(item)) {
                kept.add(item);
            }
        }
        return new KList(kept.toArray());
    }

    /** The results of transform for every element, in order. */
    public KList map(Fn transform) {
        Object[] next = new Object[items.length];
        for (int i = 0; i < items.length; i++) {
            next[i] = transform.call(items[i]);
        }
        return new KList(next);
    }

    public boolean any(Fn test) {
        return indexWhere(test) >= 0;
    }

    public boolean all(Fn test) {
        for (Object item : items) {
            if (!(Boolean) test.call(item)) {
                return false;
            }
        }
        return true;
    }

    public long count(Fn test) {
        long count = 0;
        for (Object item : items) {
            if ((Boolean) test.call(item)) {
                count++;
            }
        }
        return count;
    }

    /** The position of the first element for which test returns true, or -1. */
    public long indexWhere(Fn test) {
        for (int i = 0; i < items.length; i++) {
            if ((Boolean) test.call(items[i])) {
                return i;
            }
        }
        return -1;
    }

    /** The elements ordered by ascending key; elements with equal keys keep their order. */
    public KList sortedBy(Fn key) {
        final Object[][] pairs = new Object[items.length][];
        for (int i = 0; i < items.length; i++) {
            pairs[i] = new Object[] {key.call(items[i]), items[i]};
        }
        java.util.Arrays.sort(pairs, new java.util.Comparator<Object[]>() {
            @Override
            public int compare(Object[] a, Object[] b) {
                return Long.compare((Long) a[0], (Long) b[0]);
            }
        });
        Object[] next = new Object[items.length];
        for (int i = 0; i < items.length; i++) {
            next[i] = pairs[i][1];
        }
        return new KList(next);
    }

    public KList append(Object item) {
        Object[] next = new Object[items.length + 1];
        System.arraycopy(items, 0, next, 0, items.length);
        next[items.length] = item;
        return new KList(next);
    }
}
