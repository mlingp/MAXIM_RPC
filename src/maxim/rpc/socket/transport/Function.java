package maxim.rpc.socket.transport;

public interface Function<F, T> {
    T apply(F var);
}
