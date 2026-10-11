package com.sparrowwallet.sparrow.io;

import javafx.concurrent.Service;

import java.util.Collections;
import java.util.Set;

/** Common address-verification result contract, independent of transport. */
public abstract class DeviceDisplayAddressService extends Service<String> {
    public Set<byte[]> getNewDeviceRegistrations() {
        return Collections.emptySet();
    }
}
