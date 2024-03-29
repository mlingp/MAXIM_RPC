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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RpcProvider implements MessageProcessor<byte[]> {

    private final Map<String, Object> impMap = new HashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Map<String, Class<?>> primitiveClass = new HashMap<>();

    {
        primitiveClass.put("int", int.class);
        primitiveClass.put("double", double.class);
        primitiveClass.put("long", long.class);
    }

    @Override
    public void process(IoSession<byte[]> session, byte[] msg) {
        pool.execute(() -> {
            ObjectInput objectInput = null;
            ObjectOutput objectOutput = null;
            try {
                objectInput = new ObjectInputStream(new ByteArrayInputStream(msg));
                RpcRequest req = (RpcRequest) objectInput.readObject();

                RpcResponse resp = new RpcResponse(req.getUuid());
                try {
                    String[] paramClassList = req.getParamClassList();
                    Object[] paramObjList = req.getParams();
                    Class<?>[] classArray = null;
                    if (paramClassList != null) {
                        classArray = new Class[paramClassList.length];
                        for (int i = 0; i < classArray.length; i++) {
                            Class<?> clazz = primitiveClass.get(paramClassList[i]);
                            if (clazz == null) {
                                classArray[i] = Class.forName(paramClassList[i]);
                            } else {
                                classArray[i] = clazz;
                            }
                        }
                    }
                    Object impObj = impMap.get(req.getInterfaceClass());
                    if (impObj == null) {
                        throw new UnsupportedOperationException("can not find interface: " + req.getInterfaceClass());
                    }
                    Method method = impObj.getClass().getMethod(req.getMethod(), classArray);
                    Object obj = method.invoke(impObj, paramObjList);
                    resp.setReturnObject(obj);
                    resp.setReturnType(method.getReturnType().getName());
                } catch (InvocationTargetException e) {
                    Logger.getLogger(RpcProvider.class.getName()).log(Level.SEVERE, e.getMessage(), e);
                    resp.setException(e.getTargetException().getMessage());
                } catch (Exception e) {
                    Logger.getLogger(RpcProvider.class.getName()).log(Level.SEVERE, e.getMessage(), e);
                    resp.setException(e.getMessage());
                }
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                objectOutput = new ObjectOutputStream(byteArrayOutputStream);
                objectOutput.writeObject(resp);
                byte[] data = byteArrayOutputStream.toByteArray();
                synchronized (session) {
                    session.writeBuffer().writeInt(data.length + 4);
                    session.writeBuffer().write(data);
                    session.writeBuffer().flush();
                }

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
                if (objectOutput != null) {
                    try {
                        objectOutput.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

    }

    @Override
    public void stateEvent(IoSession<byte[]> session, StateEnum stateMachineEnum, Throwable throwable) {

    }

    public final <T> void publishService(Class<T> apiName, T apiImpl) {
        impMap.put(apiName.getName(), apiImpl);
    }
}
