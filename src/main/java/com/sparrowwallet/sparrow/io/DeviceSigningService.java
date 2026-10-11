package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.psbt.PSBT;

import javafx.concurrent.Service;

import java.util.Collections;
import java.util.Set;

/** Common signing result contract, independent of USB or remote transport. */
public abstract class DeviceSigningService extends Service<PSBT> {
    public Set<byte[]> getNewDeviceRegistrations() {
        return Collections.emptySet();
    }

    /** Implementations decide whether their in-flight operation can stop waiting. */
    public void cancelOperation() {
        // USB implementations retain their existing operation lifecycle.
    }
}
