package kata.rt;

/** Places its children on top of each other; later children are drawn above earlier ones. */
public final class Stack implements Ui {
    public final KList children;

    public Stack(KList children) {
        this.children = children;
    }
}
