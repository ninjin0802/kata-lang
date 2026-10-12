package kata.rt;

import java.util.List;

/** Reflects a new Ui tree onto existing views, reusing what has not changed kind (spec 05 #reconcile). */
public final class Reconciler<V> {
    private final Host<V> host;

    public Reconciler(Host<V> host) {
        this.host = host;
    }

    /** Builds the views for a tree that has no views yet. */
    public V mount(Ui node) {
        V view = host.create(node);
        List<Ui> children = UiNodes.children(node);
        for (int i = 0; i < children.size(); i++) {
            host.insertChild(view, i, mount(children.get(i)));
        }
        return view;
    }

    /**
     * Updates the view of oldNode to show newNode.
     *
     * @return the same view when both nodes have the same class, otherwise a newly built view
     */
    public V patch(V view, Ui oldNode, Ui newNode) {
        if (oldNode.getClass() != newNode.getClass()) {
            return mount(newNode);
        }
        host.update(view, oldNode, newNode);
        List<Ui> before = UiNodes.children(oldNode);
        List<Ui> after = UiNodes.children(newNode);
        int common = Math.min(before.size(), after.size());
        for (int i = 0; i < common; i++) {
            V child = host.childAt(view, i);
            V patched = patch(child, before.get(i), after.get(i));
            if (patched != child) {
                host.replaceChild(view, i, patched);
            }
        }
        for (int i = common; i < after.size(); i++) {
            host.insertChild(view, i, mount(after.get(i)));
        }
        for (int i = before.size() - 1; i >= after.size(); i--) {
            host.removeChild(view, i);
        }
        return view;
    }
}
