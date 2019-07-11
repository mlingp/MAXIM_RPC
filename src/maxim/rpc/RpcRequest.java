package maxim.rpc;

import java.io.Serializable;
import java.util.UUID;

public class RpcRequest implements Serializable {

    private final String uuid = UUID.randomUUID().toString();
    private String interfaceClass;
    private String method;
    private String[] paramClassList;
    private Object[] params;

    public String getInterfaceClass() {
        return interfaceClass;
    }

    public void setInterfaceClass(String interfaceClass) {
        this.interfaceClass = interfaceClass;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Object[] getParams() {
        return params;
    }

    public void setParams(Object... params) {
        this.params = params;
    }

    public String[] getParamClassList() {
        return paramClassList;
    }

    public void setParamClassList(String... paramClassList) {
        this.paramClassList = paramClassList;
    }

    public String getUuid() {
        return uuid;
    }

}
