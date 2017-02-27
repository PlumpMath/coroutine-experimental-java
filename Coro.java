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

    private static <R, T> Coro<R, T> liftState(final State<Queue<Coro<R, Unit>>, T> s) {
        return new Coro<>(k -> s.bind(k));
    }
    private static <R, T> Coro<R, T> liftIO(Supplier<T> f) {
        return new Coro<>(k -> k.apply(f.get()));
    }

    private static <R> Coro<R, Queue<Coro<R, Unit>>> getProcs() {
        return liftState(State.get());
    }
    private static <R> Coro<R, Unit> putProcs(Queue<Coro<R, Unit>> s) {
        return liftState(State.put(s));
    }

    private static <R> Coro<R, Unit> dequeue() {
        return getProcs().bind(procs -> {
            Coro p = procs.poll();
            return p == null ? pure(new Unit()) : p;
        });
    }
    private static <R> Coro<R, Unit> enqueue(Coro<R, Unit> p) {
        return Coro.<R>getProcs().bind(procs -> {
            procs.add(p);
            return putProcs(procs);
        });
    }

    public static <R> Coro<R, Unit> yield() {
        return callCC((final Function<Unit, Coro<R, Unit>> k) ->
            enqueue(k.apply(new Unit())).then(dequeue()));
    }

    public static <R> Coro<R, Unit> spawn(final Coro<R, Unit> p) {
        return callCC((final Function<Unit, Coro<R, Unit>> k) ->
            enqueue(k.apply(new Unit())).then(p).then(dequeue()));
    }

    public static <R> Coro<R, Unit> exhaust() {
        return Coro.<R>getProcs().bind((Queue<Coro<R, Unit>> procs) -> {
            if (procs.isEmpty())
                return pure(new Unit());
            else
                return Coro.<R>yield().then(exhaust());
        });
    }

    private static <R, T, U> Coro<R, T> applyBothLeft(Coro<R, T> l, Coro<R, U> r) {
        return l.bind(x -> r.bind(y -> pure(x)));
    }

    public static <T> T run(Coro<T, T> that) {
        return applyBothLeft(that, exhaust()).runContT(State::pure).eval(new LinkedList<>());
    }

    public static <R> Coro<R, Unit> printNum(int n) {
        return Coro.<R, Unit>liftIO(() -> { System.out.println(n); return new Unit(); })
            .then(Coro.<R>yield());
    }

    public static <R, T> Coro<R, Unit> replicate(int n, Coro<R, T> action) {
        if (n == 0)
            return pure(new Unit());
        else
            return action.then(replicate(n - 1, action));
    }

    public static void main(String[] args) {
        Coro<Unit, Unit> proc = Coro.<Unit>spawn(replicate(2, printNum(2)))
            .then(spawn(replicate(3, printNum(3))))
            .then(replicate(4, printNum(4)));
        run(proc);
    }
}
