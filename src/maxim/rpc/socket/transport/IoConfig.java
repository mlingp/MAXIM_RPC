package maxim.rpc.socket.transport;

import maxim.rpc.socket.MessageProcessor;
import maxim.rpc.socket.NetMonitor;
import maxim.rpc.socket.Protocol;

import java.net.SocketOption;
import java.util.HashMap;
import java.util.Map;

public final class IoConfig<T> {

    /**
     * 消息体缓存大小,字节
     */
    private int readBufferSize = 512;

    /**
     * Write缓存区容量
     */
    private int writeQueueCapacity = 512;
    /**
     * 远程服务器IP
     */
    private String host;
    /**
     * 服务器消息拦截器
     */
    private NetMonitor<T> monitor;
    /**
     * 服务器端口号
     */
    private int port = 8888;
    /**
     * 消息处理器
     */
    private MessageProcessor<T> processor;
    /**
     * 协议编解码
     */
    private Protocol<T> protocol;
    /**
     * 是否启用控制台banner
     */
    private boolean bannerEnabled = true;

    /**
     * Socket 配置
     */
    private Map<SocketOption<Object>, Object> socketOptions;

    static int getIntProperty(String property, int defaultVal) {
        String valString = System.getProperty(property);
        if (valString != null) {
            try {
                return Integer.parseInt(valString);
            } catch (NumberFormatException e) {
            }
        }
        return defaultVal;
    }

    static boolean getBoolProperty(String property, boolean defaultVal) {
        String valString = System.getProperty(property);
        if (valString != null) {
            return Boolean.parseBoolean(valString);
        }
        return defaultVal;
    }

    public final String getHost() {
        return host;
    }

    public final void setHost(String host) {
        this.host = host;
    }

    public final int getPort() {
        return port;
    }

    public final void setPort(int port) {
        this.port = port;
    }

    public NetMonitor<T> getMonitor() {
        return monitor;
    }

    public Protocol<T> getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol<T> protocol) {
        this.protocol = protocol;
    }

    public final MessageProcessor<T> getProcessor() {
        return processor;
    }

    public final void setProcessor(MessageProcessor<T> processor) {
        this.processor = processor;
        this.monitor = (processor instanceof NetMonitor) ? (NetMonitor<T>) processor : null;
    }

    public int getReadBufferSize() {
        return readBufferSize;
    }

    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    public boolean isBannerEnabled() {
        return bannerEnabled;
    }

    public void setBannerEnabled(boolean bannerEnabled) {
        this.bannerEnabled = bannerEnabled;
    }

    public Map<SocketOption<Object>, Object> getSocketOptions() {
        return socketOptions;
    }

    public void setOption(SocketOption socketOption, Object f) {
        if (socketOptions == null) {
            socketOptions = new HashMap<>();
        }
        socketOptions.put(socketOption, f);
    }

    public int getWriteQueueCapacity() {
        return writeQueueCapacity;
    }

    public void setWriteQueueCapacity(int writeQueueCapacity) {
        this.writeQueueCapacity = writeQueueCapacity;
    }

    @Override
    public String toString() {
        return "IoServerConfig{" +
                ", readBufferSize=" + readBufferSize +
                ", host='" + host + '\'' +
                ", monitor=" + monitor +
                ", port=" + port +
                ", processor=" + processor +
                ", protocol=" + protocol +
                ", bannerEnabled=" + bannerEnabled +
                ", socketOptions=" + socketOptions +
                '}';
    }

    interface Property {
        String PROJECT_NAME = "MAXIM_RPC";
        String SESSION_WRITE_CHUNK_SIZE = PROJECT_NAME + ".session.writeChunkSize";
        String BUFFER_PAGE_NUM = PROJECT_NAME + ".bufferPool.pageNum";
        String SERVER_PAGE_SIZE = PROJECT_NAME + ".server.pageSize";
        String CLIENT_PAGE_SIZE = PROJECT_NAME + ".client.pageSize";
        String SERVER_PAGE_IS_DIRECT = PROJECT_NAME + ".server.page.isDirect";
        String CLIENT_PAGE_IS_DIRECT = PROJECT_NAME + ".client.page.isDirect";
    }
}
