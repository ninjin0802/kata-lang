package kata.rt;

/** A function of one argument that returns a value. Int, Float and Bool cross it as Long, Double and Boolean. */
public interface Fn {
    Object call(Object arg);
}
