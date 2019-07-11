package maxim.rpc;

import maxim.rpc.socket.transport.IoSession;

import java.nio.ByteBuffer;
import maxim.rpc.socket.Protocol;

public class RpcProtocol implements Protocol<byte[]> {

    private static final int INTEGER_BYTES = Integer.SIZE / Byte.SIZE;

    @Override
    public byte[] decode(ByteBuffer readBuffer, IoSession<byte[]> session) {
        int remaining = readBuffer.remaining();
        if (remaining < INTEGER_BYTES) {
            return null;
        }
        int messageSize = readBuffer.getInt(readBuffer.position());
        if (messageSize > remaining) {
            return null;
        }
        byte[] data = new byte[messageSize - INTEGER_BYTES];
        readBuffer.getInt();
        readBuffer.get(data);
        return data;
    }
}
