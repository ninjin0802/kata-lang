package kata.rt;

public final class Column implements Ui {
    public final KList children;

    public Column(KList children) {
        this.children = children;
    }
}
