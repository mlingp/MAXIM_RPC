package maxim.rpc.socket;

import maxim.rpc.socket.transport.IoSession;

public interface MessageProcessor<T> {

    public void process(IoSession<T> session, T msg);

    public void stateEvent(IoSession<T> session, StateEnum stateMachineEnum, Throwable throwable);
}
