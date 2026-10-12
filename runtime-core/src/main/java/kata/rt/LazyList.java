package kata.rt;

/** A vertical list that builds only the rows on screen, for long lists. */
public final class LazyList implements Ui {
    public final Items items;

    public LazyList(Items items) {
        this.items = items;
    }
}
