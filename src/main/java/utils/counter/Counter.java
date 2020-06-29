package utils.counter;

/**
 * Using counter class instead of {@link java.lang.Integer} because wrapper classes are immutable and
 * after increment/decrement operations it will be create a new object and old one will be consumed by GC also
 * we don't need to forget about boxing/unboxing which also affects performance
 */
public class Counter {
    private int count;

    public Counter() {
        this.count = 1;
    }

    public void inc() {
        ++count;
    }

    public void dec() {
        --count;
    }

    public int count() {
        return count;
    }
}
