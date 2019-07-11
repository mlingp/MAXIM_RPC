package maxim.rpc.socket.transport;

import maxim.rpc.socket.NetMonitor;
import maxim.rpc.socket.StateEnum;

import java.nio.channels.CompletionHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WriteHandler<T> implements CompletionHandler<Integer, IoSession<T>> {

    @Override
    public void completed(final Integer result, final IoSession<T> aioSession) {
        try {
            NetMonitor<T> monitor = aioSession.getServerConfig().getMonitor();
            if (monitor != null) {
                monitor.writeMonitor(aioSession, result);
            }
            aioSession.writeToChannel();
        } catch (Exception e) {
            failed(e, aioSession);
        }

    }

    @Override
    public void failed(Throwable exc, IoSession<T> aioSession) {
        try {
            aioSession.getServerConfig().getProcessor().stateEvent(aioSession, StateEnum.OUTPUT_EXCEPTION, exc);
            aioSession.close();
        } catch (Exception e) {
            Logger.getLogger(WriteHandler.class.getName()).log(Level.SEVERE, e.getMessage(), e);
        }
    }
}
