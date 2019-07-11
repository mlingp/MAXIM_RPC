package maxim.rpc.test;

import maxim.rpc.RpcConsumer;
import maxim.rpc.RpcProtocol;
import maxim.rpc.socket.transport.IoClient;
import maxim.rpc.test.api.DemoApi;

public class Consumer {

    public static void main(String... args) throws Exception {
        RpcConsumer c= new RpcConsumer();
        IoClient<byte[]> consumer = new IoClient<>("localhost", 8888, new RpcProtocol(), c);
        consumer.start();
        DemoApi demoApi = c.getObject(DemoApi.class);
        System.out.println(demoApi.test("maxim1"));
        System.out.println(demoApi.test("maxim2"));
        System.out.println(demoApi.sum(1, 2));
        consumer.shutdown();

    }

}
