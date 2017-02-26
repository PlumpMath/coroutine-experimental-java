import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.Queue;
import java.util.LinkedList;

public class Coro<R, T> {
    private final Function<Function<T, State<Queue<Coro<R, Unit>>, R>>, State<Queue<Coro<R, Unit>>, R>> cont;

    private Coro(Function<Function<T, State<Queue<Coro<R, Unit>>, R>>, State<Queue<Coro<R, Unit>>, R>> k) {
        cont = k;
    }

    public static <R, T> Coro<R, T> pure(final T value) {
        return new Coro<>(k -> k.apply(value));
    }

    private State<Queue<Coro<R, Unit>>, R> runContT(Function<T, State<Queue<Coro<R, Unit>>, R>> f) {
        return cont.apply(f);
    }

    public <U> Coro<R, U> bind(final Function<T, Coro<R, U>> f) {
        return new Coro<>((final Function<U, State<Queue<Coro<R, Unit>>, R>> k) ->
                runContT(t ->
                    f.apply(t).runContT(k))
        );
    }

    public <U> Coro<R, U> then(Coro<R, U> m) {
        return bind(dummy -> m);
    }

    public static <R, T, U> Coro<R, T> callCC(Function<Function<T, Coro<R, U>>, Coro<R, T>> f) {
        return new Coro<>((final Function<T, State<Queue<Coro<R, Unit>>, R>> k) ->
            f.apply(x -> new Coro<>(dummy -> k.apply(x))).runContT(k));
    }

    private static <R, T, S> Coro<R, T> liftState(final State<S, T> s) {
        return new Coro<>(k -> s.bind(k));
    }
    private static <R, T> Coro<R, T> liftIO(Supplier<T> f) {
        return new Coro<>(k -> k.apply(f.get()));
    }

    private static final Coro<?, Queue<Coro<?, Unit>>> getProcs = liftState(State.get);
    private static <R> Coro<R, Unit> putProcs(Queue<Coro<R, Unit>> s) {
        return liftState(State.put(s));
    }

    private static final Coro dequeue =
        getProcs.bind(procs -> {
            Coro p = procs.poll();
            return p == null ? pure(new Unit()) : p;
        });
    private static <R> Coro<R, Unit> enqueue(Coro<R, Unit> p) {
        return getProcs.bind(procs -> {
            procs.add(p);
            return putProcs(procs);
        });
    }

    public static final Coro yield =
        callCC((final Function<Unit, Coro<?, ?>> k) ->
            enqueue(k.apply(new Unit())).then(dequeue));

    public static <R> Coro<R, Unit> spawn(final Coro<R, Unit> p) {
        return callCC((final Function<Unit, Coro<R, ?>> k) ->
            enqueue(k.apply(new Unit())).then(p).then(dequeue));
    }

    public static final Coro exhaust =
        getProcs.bind(procs -> {
            if (procs.isEmpty())
                return pure(new Unit());
            else
                return yield.then(exhaust);
        });

    private static <R, T, U> Coro<R, T> applyBothLeft(Coro<R, T> l, Coro<R, U> r) {
        return l.bind(x -> r.bind(y -> pure(x)));
    }

    public T run() {
        return applyBothLeft(this, exhaust).runContT(State::pure).eval(new LinkedList());
    }

    public static <R> Coro<R, Unit> printNum(int n) {
        return liftIO(() -> { System.out.println(n); return null; })
            .then(yield);
    }

    public static <R, T> Coro<R, Unit> replicate(int n, Coro<R, T> action) {
        if (n == 0)
            return pure(new Unit());
        else
            return action.then(replicate(n - 1, action));
    }

    public static void main(String[] args) {
        spawn(replicate(2, printNum(2)))
            .then(spawn(replicate(2, printNum(3))))
            .then(replicate(3, printNum(3)));
    }
}
