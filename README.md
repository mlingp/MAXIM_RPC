# MAXIM_RPC
RPC工具包

       //服务端
        RpcProvider p = new RpcProvider();
        IoServer<byte[]> server = new IoServer<>(8888, new RpcProtocol(), p);
        server.start();
        p.publishService(DemoApi.class, new DemoApiImpl());
        
       //客户端 
        RpcConsumer c= new RpcConsumer();
        IoClient<byte[]> consumer = new IoClient<>("localhost", 8888, new RpcProtocol(), c);
        consumer.start();
        DemoApi demoApi = c.getObject(DemoApi.class);
        System.out.println(demoApi.test("张三"));
        System.out.println(demoApi.test("李四"));
        System.out.println(demoApi.sum(1, 2));
        consumer.shutdown();
