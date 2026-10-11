package com.sparrowwallet.sparrow.io.argus;

import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.sparrow.control.ArgusPinDialog;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.DeviceSigningService;
import com.sparrowwallet.sparrow.io.SignTransactionException;

import javafx.concurrent.Task;
import javafx.stage.Window;

/** Request-bound progress and PIN UI belong to the remote signer's service. */
final class ArgusSigningService extends DeviceSigningService {
    private final Device device;
    private final String passphrase;
    private final PSBT psbt;
    private final Window owner;

    ArgusSigningService(Device device, String passphrase, PSBT psbt, Window owner) {
        this.device = device;
        this.passphrase = passphrase;
        this.psbt = psbt;
        this.owner = owner;
    }

    @Override
    public void cancelOperation() {
        if(isRunning()) cancel();
    }

    @Override
    protected Task<PSBT> createTask() {
        return new Task<>() {
            @Override
            protected PSBT call() throws SignTransactionException {
                try {
                    return ArgusApi.signPSBT(
                            device,
                            passphrase,
                            psbt,
                            progress ->
                                    updateMessage(
                                            progress.status()
                                                    + (progress.reason().isEmpty()
                                                            ? ""
                                                            : " — " + progress.reason())),
                            new ArgusPinDialog(owner));
                } catch(Exception e) {
                    throw new SignTransactionException(e.getMessage(), e);
                }
            }
        };
    }
}
