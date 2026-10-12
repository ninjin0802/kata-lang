package kata.rt;

/** Lays out its children in rows of equal-width columns. */
public final class Grid implements Ui {
    public final long columns;
    public final KList children;

    public Grid(long columns, KList children) {
        this.columns = columns;
        this.children = children;
    }
}
