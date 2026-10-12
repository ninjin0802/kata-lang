package kata.rt;

/** The rows of a LazyList: how many there are and how to build each one. */
public interface Items {
    long size();

    Ui item(long index);
}
