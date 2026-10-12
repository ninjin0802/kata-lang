package kata.rt;

/** A button that opens a pop-up menu of options. */
public final class Menu implements Ui {
    public final String label;
    public final KList options;
    public final Handler onSelect;

    public Menu(String label, KList options, Handler onSelect) {
        this.label = label;
        this.options = options;
        this.onSelect = onSelect;
    }
}
