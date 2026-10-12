package kata.rt;

/** A raised surface with rounded corners behind its child. */
public final class Card implements Ui {
    public final Ui child;

    public Card(Ui child) {
        this.child = child;
    }
}
