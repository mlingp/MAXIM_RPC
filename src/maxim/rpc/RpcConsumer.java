package maxim.rpc;

import maxim.rpc.socket.transport.IoSession;
import maxim.rpc.socket.MessageProcessor;
import maxim.rpc.socket.StateEnum;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.lang.reflect.Proxy;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RpcConsumer implements MessageProcessor<byte[]> {

    private final Map<String, CompletableFuture<RpcResponse>> synchRespMap = new ConcurrentHashMap<>();
    private final Map<Class, Object> objectMap = new ConcurrentHashMap<>();
    private IoSession<byte[]> ioSession;

    public static void main(String[] args) {
        CompletableFuture<Integer> completableFuture = new CompletableFuture<>();
        new Thread(() -> {
            try {
                System.out.println(completableFuture.get());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            completableFuture.complete(null);
        }).start();
    }

    @Override
    public void process(IoSession<byte[]> session, byte[] msg) {
        ObjectInput objectInput = null;
        try {
            objectInput = new ObjectInputStream(new ByteArrayInputStream(msg));
            RpcResponse resp = (RpcResponse) objectInput.readObject();
            synchRespMap.get(resp.getUuid()).complete(resp);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (objectInput != null) {
                try {
                    objectInput.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public <T> T getObject(final Class<T> remoteInterface) {
        Object obj = objectMap.get(remoteInterface);
        if (obj != null) {
            return (T) obj;
        }
        obj = (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{remoteInterface},
                (proxy, method, args) -> {
                    RpcRequest req = new RpcRequest();
                    req.setInterfaceClass(remoteInterface.getName());
                    req.setMethod(method.getName());
                    Class<?>[] types = method.getParameterTypes();
                    if (types.length > 0) {
                        String[] paramClass = new String[types.length];
                        for (int i = 0; i < types.length; i++) {
                            paramClass[i] = types[i].getName();
                        }
                        req.setParamClassList(paramClass);
                    }
                    req.setParams(args);
                    RpcResponse rmiResp = sendRpcRequest(req);
                    return rmiResp.getReturnObject();
                });
        objectMap.put(remoteInterface, obj);
        return (T) obj;
    }

    private RpcResponse sendRpcRequest(RpcRequest request) throws Exception {
        CompletableFuture<RpcResponse> rpcResponseCompletableFuture = new CompletableFuture<>();
        synchRespMap.put(request.getUuid(), rpcResponseCompletableFuture);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutput objectOutput = new ObjectOutputStream(byteArrayOutputStream);
        objectOutput.writeObject(request);
        byte[] data = byteArrayOutputStream.toByteArray();
        synchronized (ioSession) {
            ioSession.writeBuffer().writeInt(data.length + 4);
            ioSession.writeBuffer().write(data);
            ioSession.writeBuffer().flush();
        }
        try {
            RpcResponse resp = rpcResponseCompletableFuture.get(3, TimeUnit.SECONDS);
            return resp;
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new SocketTimeoutException("Message is timeout!");
        }
    }

    @Override
    public void stateEvent(IoSession<byte[]> session, StateEnum stateMachineEnum, Throwable throwable) {
        switch (stateMachineEnum) {
            case NEW_SESSION:
                this.ioSession = session;
                break;
        }
    }

}
