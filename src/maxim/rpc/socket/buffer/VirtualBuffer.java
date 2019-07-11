package maxim.rpc.socket.buffer;

import java.nio.ByteBuffer;

public final class VirtualBuffer {

    private final BufferPage bufferPage;
    private ByteBuffer buffer;
    private boolean clean = false;
    private int parentPosition;
    private int parentLimit;

    public VirtualBuffer(BufferPage bufferPage, ByteBuffer buffer, int parentPosition, int parentLimit) {
        this.bufferPage = bufferPage;
        this.buffer = buffer;
        this.parentPosition = parentPosition;
        this.parentLimit = parentLimit;
    }

    int getParentPosition() {
        return parentPosition;
    }

    void setParentPosition(int parentPosition) {
        this.parentPosition = parentPosition;
    }

    int getParentLimit() {
        return parentLimit;
    }

    void setParentLimit(int parentLimit) {
        this.parentLimit = parentLimit;
    }

    public ByteBuffer buffer() {
        return buffer;
    }

    void buffer(ByteBuffer buffer) {
        this.buffer = buffer;
        clean = false;
    }

    public void clean() {
        if (clean) {
            System.err.println("buffer has cleaned");
            throw new RuntimeException();
        }
        clean = true;
        if (bufferPage != null) {
            bufferPage.clean(this);
        }
    }

    @Override
    public String toString() {
        return "VirtualBuffer{" +
                "parentPosition=" + parentPosition +
                ", parentLimit=" + parentLimit +
                '}';
    }
}
