package com.sparrowwallet.sparrow.io;

import javafx.concurrent.Service;

import java.util.Collections;
import java.util.Set;

/** A device operation's result and optional wallet registrations, independent of transport. */
public abstract class DeviceService<T> extends Service<T> {
    public Set<byte[]> getNewDeviceRegistrations() {
        return Collections.emptySet();
    }
}
