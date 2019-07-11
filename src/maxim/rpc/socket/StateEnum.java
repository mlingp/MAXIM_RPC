package maxim.rpc.socket;

//各类状态枚举
public enum StateEnum {

    //连接已建立并构建Session对象
    NEW_SESSION,
    //读通道已关闭
    INPUT_SHUTDOWN,
    //业务处理异常
    PROCESS_EXCEPTION,
    //协议解码异常
    DECODE_EXCEPTION,
    //读操作异常
    INPUT_EXCEPTION,
    //写操作异常
    OUTPUT_EXCEPTION,
    //会话关闭中
    SESSION_CLOSING,
    //会话关闭成功
    SESSION_CLOSED,
    //拒绝接受连接
    REJECT_ACCEPT;

}
