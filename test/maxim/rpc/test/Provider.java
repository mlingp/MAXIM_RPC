package maxim.rpc.test;

import java.io.IOException;
import maxim.rpc.RpcProtocol;
import maxim.rpc.RpcProvider;
import maxim.rpc.socket.transport.IoServer;
import maxim.rpc.test.api.DemoApi;
import maxim.rpc.test.api.DemoApiImpl;

public class Provider {
    public static void main(String[] args) throws IOException {
        RpcProvider p = new RpcProvider();
        IoServer<byte[]> server = new IoServer<>(8888, new RpcProtocol(), p);
        server.start();
        p.publishService(DemoApi.class, new DemoApiImpl());
    }
}
