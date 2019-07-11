package maxim.rpc.socket;

import maxim.rpc.socket.transport.IoSession;

import java.nio.channels.AsynchronousSocketChannel;

//监控接口
public interface NetMonitor<T> {

    public boolean acceptMonitor(AsynchronousSocketChannel channel);

    public void readMonitor(IoSession<T> session, int readSize);

    public void writeMonitor(IoSession<T> session, int writeSize);

}
