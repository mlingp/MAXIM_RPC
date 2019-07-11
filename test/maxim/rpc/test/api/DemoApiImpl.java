package maxim.rpc.test.api;


public class DemoApiImpl implements DemoApi {
    
    @Override
    public String test(String name) {
        System.out.println(name);
        return "你好！ " + name;
    }

    @Override
    public int sum(int a, int b) {
        System.out.println(a + " " + b);
        return a + b;
    }
}
