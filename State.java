import java.util.function.Function;

public class State<S, T> {
    private Function<S, StateResult<S, T>> func;
    public State(Function<S, StateResult<S, T>> f) {
        func = f;
    }
    public StateResult<S, T> run(S init) {
        return func.apply(init);
    }
    public T eval(S init) {
        return run(init).result;
    }
    public static <S, T> State<S, T> pure(T value) {
        return new State<>(s -> new StateResult(value, s));
    }
    public <U> State<S, U> bind(Function<T, State<S, U>> f) {
        return new State<>(init -> {
            StateResult<S, T> inter = run(init);
            return f.apply(inter.result).run(inter.state);
        });
    }
    public <U> State<S, U> then(State<S, U> m) {
        return bind(dummy -> m);
    }
    public static <S> State<S, S> get() {
        return new State<>(s -> new StateResult(s, s));
    }
    public static <S> State<S, Unit> put(S s) {
        return new State<>(dummy -> new StateResult(new Unit(), s));
    }
}
