package maxim.rpc.socket.transport;

import maxim.rpc.socket.MessageProcessor;
import maxim.rpc.socket.StateEnum;
import maxim.rpc.socket.buffer.BufferPage;
import maxim.rpc.socket.buffer.VirtualBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

//IO传输层会话。
public class IoSession<T> {

    public static final byte SESSION_STATUS_CLOSED = 1;
    public static final byte SESSION_STATUS_CLOSING = 2;
    public static final byte SESSION_STATUS_ENABLED = 3;
    public AsynchronousSocketChannel channel;
    public VirtualBuffer readBuffer;
    public VirtualBuffer writeBuffer;
    public byte status = SESSION_STATUS_ENABLED;
    private Semaphore semaphore = new Semaphore(1);
    private Object attachment;
    volatile boolean threadLocal = false;
    private ReadHandler<T> readCompletionHandler;
    private WriteHandler<T> writeCompletionHandler;
    private IoConfig<T> ioServerConfig;
    private InputStream inputStream;
    private WriteBuffer byteBuf;

   public  IoSession(AsynchronousSocketChannel channel, final IoConfig<T> config, 
           ReadHandler<T> readCompletionHandler, WriteHandler<T> writeCompletionHandler, BufferPage bufferPage) {
        this.channel = channel;
        this.readCompletionHandler = readCompletionHandler;
        this.writeCompletionHandler = writeCompletionHandler;
        this.ioServerConfig = config;

        this.readBuffer = bufferPage.allocate(config.getReadBufferSize());
        byteBuf = new WriteBuffer(bufferPage, (WriteBuffer var) -> {
            if (!semaphore.tryAcquire()) {
                return null;
            }
            IoSession.this.writeBuffer = var.poll();
            if (writeBuffer == null) {
                semaphore.release();
            } else {
                continueWrite(writeBuffer);
            }
            return null;
        }, ioServerConfig.getWriteQueueCapacity());
        //触发状态机
        config.getProcessor().stateEvent(this, StateEnum.NEW_SESSION, null);
    }

    void initSession() {
        continueRead();
    }

    void writeToChannel() {
        if (writeBuffer == null) {
            writeBuffer = byteBuf.poll();
        } else if (!writeBuffer.buffer().hasRemaining()) {
            writeBuffer.clean();
            writeBuffer = byteBuf.poll();
        }

        if (writeBuffer != null) {
            continueWrite(writeBuffer);
            return;
        }
        semaphore.release();
        //此时可能是Closing或Closed状态
        if (status != SESSION_STATUS_ENABLED) {
            close();
        } else if (!byteBuf.isClosed()) {
            //也许此时有新的消息通过write方法添加到writeCacheQueue中
            byteBuf.flush();
        }
    }

    public final void readFromChannel0(ByteBuffer buffer) {
        channel.read(buffer, this, readCompletionHandler);
    }

    public final void writeToChannel0(ByteBuffer buffer) {
        channel.write(buffer, 0L, TimeUnit.MILLISECONDS, this, writeCompletionHandler);
    }

    public final WriteBuffer writeBuffer() {
        return byteBuf;
    }

    public final void close() {
        close(true);
    }

    public synchronized void close(boolean immediate) {
        if (status == SESSION_STATUS_CLOSED) {
            return;
        }
        status = immediate ? SESSION_STATUS_CLOSED : SESSION_STATUS_CLOSING;
        if (immediate) {
            try {
                if (!byteBuf.isClosed()) {
                    byteBuf.close();
                }
                byteBuf = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
            readBuffer.clean();
            readBuffer = null;
            if (writeBuffer != null) {
                writeBuffer.clean();
                writeBuffer = null;
            }
            try {
                channel.shutdownInput();
                channel.shutdownOutput();
                channel.close();
            } catch (IOException e) {
                Logger.getLogger(IoSession.class.getName()).log(Level.SEVERE, e.getMessage(), e);
            }
            ioServerConfig.getProcessor().stateEvent(this, StateEnum.SESSION_CLOSED, null);
        } else if ((writeBuffer == null || !writeBuffer.buffer().hasRemaining()) && !byteBuf.hasData()) {
            close(true);
        } else {
            ioServerConfig.getProcessor().stateEvent(this, StateEnum.SESSION_CLOSING, null);
            if (!byteBuf.isClosed()) {
                byteBuf.flush();
            }
        }
    }

    public final String getSessionID() {
        return "ioSession-" + hashCode();
    }

    public final boolean isInvalid() {
        return status != SESSION_STATUS_ENABLED;
    }

    public void readFromChannel(boolean eof) {
        if (status == SESSION_STATUS_CLOSED) {
            return;
        }
        final ByteBuffer readBuffer = this.readBuffer.buffer();
        readBuffer.flip();
        final MessageProcessor<T> messageProcessor = ioServerConfig.getProcessor();
        while (readBuffer.hasRemaining() && status == SESSION_STATUS_ENABLED) {
            T dataEntry = null;
            try {
                dataEntry = ioServerConfig.getProtocol().decode(readBuffer, this);
            } catch (Exception e) {
                messageProcessor.stateEvent(this, StateEnum.DECODE_EXCEPTION, e);
                throw e;
            }
            if (dataEntry == null) {
                break;
            }

            //处理消息
            try {
                messageProcessor.process(this, dataEntry);
            } catch (Exception e) {
                messageProcessor.stateEvent(this, StateEnum.PROCESS_EXCEPTION, e);
            }
        }

        if (eof || status == SESSION_STATUS_CLOSING) {
            close(false);
            messageProcessor.stateEvent(this, StateEnum.INPUT_SHUTDOWN, null);
            return;
        }
        if (status == SESSION_STATUS_CLOSED) {
            return;
        }

        //数据读取完毕
        if (readBuffer.remaining() == 0) {
            readBuffer.clear();
        } else if (readBuffer.position() > 0) {
            // 仅当发生数据读取时调用compact,减少内存拷贝
            readBuffer.compact();
        } else {
            readBuffer.position(readBuffer.limit());
            readBuffer.limit(readBuffer.capacity());
        }

        //读缓冲区已满
        if (!readBuffer.hasRemaining()) {
            RuntimeException exception = new RuntimeException("readBuffer has no remaining");
            messageProcessor.stateEvent(this, StateEnum.DECODE_EXCEPTION, exception);
            throw exception;
        }

        if (byteBuf != null && !byteBuf.isClosed()) {
            byteBuf.flush();
        }
        continueRead();
    }

    public void continueRead() {
        readFromChannel0(readBuffer.buffer());
    }

    public void continueWrite(VirtualBuffer writeBuffer) {
        writeToChannel0(writeBuffer.buffer());
    }

    public final <T> T getAttachment() {
        return (T) attachment;
    }

    public final <T> void setAttachment(T attachment) {
        this.attachment = attachment;
    }

    public final InetSocketAddress getLocalAddress() throws IOException {
        assertChannel();
        return (InetSocketAddress) channel.getLocalAddress();
    }

    public final InetSocketAddress getRemoteAddress() throws IOException {
        assertChannel();
        return (InetSocketAddress) channel.getRemoteAddress();
    }

    private void assertChannel() throws IOException {
        if (status == SESSION_STATUS_CLOSED || channel == null) {
            throw new IOException("session is closed");
        }
    }

    IoConfig<T> getServerConfig() {
        return this.ioServerConfig;
    }

    public InputStream getInputStream() throws IOException {
        return inputStream == null ? getInputStream(-1) : inputStream;
    }

    public InputStream getInputStream(int length) throws IOException {
        if (inputStream != null) {
            throw new IOException("pre inputStream has not closed");
        }
        if (inputStream != null) {
            return inputStream;
        }
        synchronized (this) {
            if (inputStream == null) {
                inputStream = new InnerInputStream(length);
            }
        }
        return inputStream;
    }

    private class InnerInputStream extends InputStream {

        private int remainLength;

        public InnerInputStream(int length) {
            this.remainLength = length >= 0 ? length : -1;
        }

        @Override
        public int read() throws IOException {
            if (remainLength == 0) {
                return -1;
            }
            ByteBuffer readBuffer = IoSession.this.readBuffer.buffer();
            if (readBuffer.hasRemaining()) {
                remainLength--;
                return readBuffer.get();
            }
            readBuffer.clear();

            try {
                int readSize = channel.read(readBuffer).get();
                readBuffer.flip();
                if (readSize == -1) {
                    remainLength = 0;
                    return -1;
                } else {
                    return read();
                }
            } catch (IOException | InterruptedException | ExecutionException e) {
                throw new IOException(e);
            }
        }

        @Override
        public int available() throws IOException {
            return remainLength == 0 ? 0 : readBuffer.buffer().remaining();
        }

        @Override
        public void close() throws IOException {
            if (IoSession.this.inputStream == InnerInputStream.this) {
                IoSession.this.inputStream = null;
            }
        }
    }
}
