package maxim.rpc.socket;

import maxim.rpc.socket.transport.IoSession;

import java.nio.ByteBuffer;

//消息传输协议接口
public interface Protocol<T> {
    public T decode(final ByteBuffer readBuffer, IoSession<T> session);
}
