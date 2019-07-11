package maxim.rpc.socket.transport;

import maxim.rpc.socket.NetMonitor;
import maxim.rpc.socket.StateEnum;

import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReadHandler<T> implements CompletionHandler<Integer, IoSession<T>> {

    private ExecutorService executorService;
    private Semaphore semaphore;

    public ReadHandler() {
    }

    public ReadHandler(ExecutorService executorService, Semaphore semaphore) {
        this.executorService = executorService;
        this.semaphore = semaphore;
    }

    @Override
    public void completed(final Integer result, final IoSession<T> aioSession) {
        if (executorService == null || aioSession.threadLocal) {
            completed0(result, aioSession);
            return;
        }

        if (semaphore == null || !semaphore.tryAcquire()) {
            executorService.execute(() -> {
                completed0(result, aioSession);
            });
            return;
        }
        aioSession.threadLocal = true;
        try {
            completed0(result, aioSession);
        } finally {
            aioSession.threadLocal = false;
            semaphore.release();
        }

    }

    private void completed0(final Integer result, final IoSession<T> aioSession) {
        try {
            NetMonitor<T> monitor = aioSession.getServerConfig().getMonitor();
            if (monitor != null) {
                monitor.readMonitor(aioSession, result);
            }
            aioSession.readFromChannel(result == -1);
        } catch (Exception e) {
            failed(e, aioSession);
        }
    }

    @Override
    public void failed(Throwable exc, IoSession<T> aioSession) {

        try {
            aioSession.getServerConfig().getProcessor().stateEvent(aioSession, StateEnum.INPUT_EXCEPTION, exc);
             aioSession.close(false);
        } catch (Exception e) {
            Logger.getLogger(ReadHandler.class.getName()).log(Level.SEVERE, e.getMessage(), e);
        }
    }
}