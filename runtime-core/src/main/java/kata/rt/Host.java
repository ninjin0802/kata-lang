package kata.rt;

/** What the reconciler needs from the platform that actually draws. V is the platform's view type. */
public interface Host<V> {
    /** Creates the view for this node alone; its children are inserted afterwards. */
    V create(Ui node);

    /** Brings a view up to date with a new node of the same class. Called even when nothing changed. */
    void update(V view, Ui oldNode, Ui newNode);

    void insertChild(V parent, int index, V child);

    void removeChild(V parent, int index);

    void replaceChild(V parent, int index, V child);

    V childAt(V parent, int index);
}
