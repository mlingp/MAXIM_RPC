package maxim.rpc.socket.transport;

import maxim.rpc.socket.MessageProcessor;
import maxim.rpc.socket.NetMonitor;
import maxim.rpc.socket.Protocol;
import maxim.rpc.socket.StateEnum;
import maxim.rpc.socket.buffer.BufferPagePool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IoServer<T> {

    protected IoConfig<T> config = new IoConfig<>();
    protected BufferPagePool bufferPool;
    protected ReadHandler<T> aioReadCompletionHandler;
    protected WriteHandler<T> aioWriteCompletionHandler;
    private ExecutorService workerExecutorService;
    private Function<AsynchronousSocketChannel, IoSession<T>> aioSessionFunction;
    private AsynchronousServerSocketChannel serverSocketChannel = null;
    private AsynchronousChannelGroup asynchronousChannelGroup;
    private Thread acceptThread = null;
    private volatile boolean running = true;
    private int bossThreadNum = Runtime.getRuntime().availableProcessors() < 4 ? 3 : Runtime.getRuntime().availableProcessors();
    private int bossShareToWorkerThreadNum = bossThreadNum > 4 ? bossThreadNum >> 2 : bossThreadNum - 2;
    private int workerThreadNum = bossThreadNum - bossShareToWorkerThreadNum;

    public IoServer(int port, Protocol<T> protocol, MessageProcessor<T> messageProcessor) {
        config.setPort(port);
        config.setProtocol(protocol);
        config.setProcessor(messageProcessor);
    }

    public IoServer(String host, int port, Protocol<T> protocol, MessageProcessor<T> messageProcessor) {
        this(port, protocol, messageProcessor);
        config.setHost(host);
    }

    public void start() throws IOException {

        start0((AsynchronousSocketChannel channel)
                -> new IoSession<>(channel, config, aioReadCompletionHandler, aioWriteCompletionHandler, bufferPool.allocateBufferPage()));
    }

    protected final void start0(Function<AsynchronousSocketChannel, IoSession<T>> aioSessionFunction) throws IOException {
        try {
            if (bossShareToWorkerThreadNum >= bossThreadNum) {
                bossShareToWorkerThreadNum = 0;
            }
            workerExecutorService = Executors.newFixedThreadPool(workerThreadNum, new ThreadFactory() {
                byte index = 0;

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "MAXIM_RPC:WorkerThread-" + (++index));
                }
            });
            aioReadCompletionHandler = new ReadHandler<>(workerExecutorService, bossShareToWorkerThreadNum
                    > 0 && bossShareToWorkerThreadNum < bossThreadNum ? new Semaphore(bossShareToWorkerThreadNum) : null);
            aioWriteCompletionHandler = new WriteHandler<>();

            this.bufferPool = new BufferPagePool(IoConfig.getIntProperty(IoConfig.Property.SERVER_PAGE_SIZE, 1024 * 1024),
                    IoConfig.getIntProperty(IoConfig.Property.BUFFER_PAGE_NUM, bossThreadNum + workerThreadNum),
                    IoConfig.getBoolProperty(IoConfig.Property.SERVER_PAGE_IS_DIRECT, true));
            this.aioSessionFunction = aioSessionFunction;
            asynchronousChannelGroup = AsynchronousChannelGroup.withFixedThreadPool(bossThreadNum, new ThreadFactory() {
                byte index = 0;

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "MAXIM_RPC:BossThread-" + (++index));
                }
            });
            this.serverSocketChannel = AsynchronousServerSocketChannel.open(asynchronousChannelGroup);
            if (config.getSocketOptions() != null) {
                for (Map.Entry<SocketOption<Object>, Object> entry : config.getSocketOptions().entrySet()) {
                    this.serverSocketChannel.setOption(entry.getKey(), entry.getValue());
                }
            }
            if (config.getHost() != null) {
                serverSocketChannel.bind(new InetSocketAddress(config.getHost(), config.getPort()), 1000);
            } else {
                serverSocketChannel.bind(new InetSocketAddress(config.getPort()), 1000);
            }
            acceptThread = new Thread(new Runnable() {
                NetMonitor<T> monitor = config.getMonitor();

                @Override
                public void run() {
                    while (running) {
                        Future<AsynchronousSocketChannel> future = serverSocketChannel.accept();
                        try {
                            final AsynchronousSocketChannel channel = future.get();
                            workerExecutorService.execute(() -> {
                                if (monitor == null || monitor.acceptMonitor(channel)) {
                                    createSession(channel);
                                } else {
                                    config.getProcessor().stateEvent(null, StateEnum.REJECT_ACCEPT, null);
                                    closeChannel(channel);
                                }
                            });
                        } catch (InterruptedException | ExecutionException e) {
                            Logger.getLogger(IoServer.class.getName()).log(Level.SEVERE, "AcceptThread Exception", e);
                        }

                    }
                }
            }, "MAXIM_RPC:AcceptThread");
            acceptThread.start();
        } catch (IOException e) {
            shutdown();
            throw e;
        }

    }

    private void createSession(AsynchronousSocketChannel channel) {
        IoSession<T> session = null;
        try {
            session = aioSessionFunction.apply(channel);
            session.initSession();
        } catch (Exception e1) {
            Logger.getLogger(IoServer.class.getName()).log(Level.SEVERE, e1.getMessage(), e1);
            if (session == null) {
                closeChannel(channel);
            } else {
                session.close();
            }
        }
    }

    private void closeChannel(AsynchronousSocketChannel channel) {
        try {
            channel.shutdownInput();
            channel.shutdownOutput();
            channel.close();
        } catch (IOException e) {
            Logger.getLogger(IoServer.class.getName()).log(Level.SEVERE, "close channel exception", e);
        }
    }

    public final void shutdown() {
        running = false;
        try {
            if (serverSocketChannel != null) {
                serverSocketChannel.close();
                serverSocketChannel = null;
            }
        } catch (IOException e) {
            Logger.getLogger(IoServer.class.getName()).log(Level.SEVERE, e.getMessage(), e);
        }
        if (!workerExecutorService.isTerminated()) {
            try {
                workerExecutorService.shutdownNow();
            } catch (Exception e) {
                Logger.getLogger(IoServer.class.getName()).log(Level.SEVERE, "shutdown exception", e);
            }
        }
        if (!asynchronousChannelGroup.isTerminated()) {
            try {
                asynchronousChannelGroup.shutdownNow();
            } catch (IOException e) {
                Logger.getLogger(IoServer.class.getName()).log(Level.SEVERE, "shutdown exception", e);
            }
        }
        try {
            asynchronousChannelGroup.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
           Logger.getLogger(IoServer.class.getName()).log(Level.SEVERE, "shutdown exception", e);
        }
    }

    public final IoServer<T> setWorkerThreadNum(int num) {
        this.workerThreadNum = num;
        return this;
    }

    public final IoServer<T> setBossShareToWorkerThreadNum(int num) {
        this.bossShareToWorkerThreadNum = num;
        return this;
    }

    public final IoServer<T> setReadBufferSize(int size) {
        this.config.setReadBufferSize(size);
        return this;
    }

    public final IoServer<T> setBannerEnabled(boolean bannerEnabled) {
        config.setBannerEnabled(bannerEnabled);
        return this;
    }

    public final <V> IoServer<T> setOption(SocketOption<V> socketOption, V value) {
        config.setOption(socketOption, value);
        return this;
    }

    public final IoServer<T> setWriteQueueCapacity(int writeQueueCapacity) {
        config.setWriteQueueCapacity(writeQueueCapacity);
        return this;
    }

    public final IoServer<T> setBossThreadNum(int threadNum) {
        this.bossThreadNum = threadNum;
        return this;
    }
}
