package utils.function;

import utils.counter.Counter;

/**
 * Write own function regardless {@link java.util.function.Function} because we can use only wrapper classes in generics
 * and for primitive types it isn't unnecessary boxing/unboxing operations
 * @param <T>
 * @see Counter
 */
@FunctionalInterface
public interface BooleanReturnFunction<T> {
    boolean apply(T value);
}
