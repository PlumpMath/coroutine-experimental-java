// package org.nicball.coro;

import java.util.function.Function;
import java.util.function.Consumer;

public class Cont<R, T> {
    private final Function<Function<T, R>, R> cont;

    public Cont(Function<Function<T, R>, R> k) {
        cont = k;
    }

    public static <R, T> Cont<R, T> pure(final T value) {
        return new Cont<>((k) -> k.apply(value));
    }

    public R run(Function<T, R> f) {
        return cont.apply(f);
    }

    public void runVoid(final Consumer<T> f) {
        cont.apply((t) -> { f.accept(t); return null; });
    }

    public <U> Cont<R, U> bind(final Function<T, Cont<R, U>> f) {
        return new Cont<>((final Function<U, R> k) ->
                run((t) ->
                    f.apply(t).run(k))
        );
    }

    public <U> Cont<R, U> then(Cont<R, U> m) {
        return bind((dummy) -> m);
    }

    public static <R, T, U> Cont<R, T> callCC(Function<Function<T, Cont<R, U>>, Cont<R, T>> f) {
        return new Cont<>((final Function<T, R> k) ->
            f.apply((x) -> new Cont<>((dummy) -> k.apply(x))).run(k));
    }

    public static void main(String[] args) {
        pure(5).bind((x) -> {
            System.out.println(x);
            return callCC((k) ->
                k.apply("Haha")
                 .then(pure("Never see this")));
        }).runVoid(System.out::println);
    }
}
