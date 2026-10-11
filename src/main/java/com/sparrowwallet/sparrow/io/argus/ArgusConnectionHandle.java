package com.sparrowwallet.sparrow.io.argus;

import com.sparrowwallet.lark.ArgusClient;
import com.sparrowwallet.lark.DeviceException;

import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps callbacks and the entire host operation exclusive to one paired signer. */
final class ArgusConnectionHandle implements AutoCloseable {
    private final ArgusClient client;
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile boolean closed;

    ArgusConnectionHandle(ArgusClient client) {
        this.client = client;
    }

    <T> T execute(Operation<T> operation) throws Exception {
        if(!busy.compareAndSet(false, true)) {
            throw new DeviceException("An Argus request is already active");
        }

        try {
            if(closed) {
                throw new DeviceException("Argus connection closed");
            }
            return operation.apply(client);
        } finally {
            busy.set(false);
        }
    }

    @Override
    public void close() {
        closed = true;
        client.getConnection().close();
    }

    @FunctionalInterface
    interface Operation<T> {
        T apply(ArgusClient client) throws Exception;
    }
}
