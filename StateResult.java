public class StateResult<S, T> {
    public final S state;
    public final T result;
    public StateResult(T t, S s) {
        state = s;
        result = t;
    }
}
