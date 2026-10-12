package kata.rt;

/** While shown, runs onTick every millis milliseconds. */
public final class Timer implements Ui {
    public final long millis;
    public final Action onTick;
    public final Ui child;

    public Timer(long millis, Action onTick, Ui child) {
        this.millis = millis;
        this.onTick = onTick;
        this.child = child;
    }
}
