package com.sparrowwallet.sparrow.io.argus;

import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.sparrow.control.ArgusPinDialog;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.DeviceService;
import com.sparrowwallet.sparrow.io.SignTransactionException;

import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/** Request-bound progress and PIN UI belong to the remote signer's service. */
final class ArgusSigningService extends DeviceService<PSBT> {
    private final Device device;
    private final String passphrase;
    private final PSBT psbt;
    private final ArgusPinDialog pinDialog;

    ArgusSigningService(Device device, String passphrase, PSBT psbt, Window owner) {
        this.device = device;
        this.passphrase = passphrase;
        this.psbt = psbt;
        this.pinDialog = new ArgusPinDialog(owner);

        // Own only this worker's lifetime. Cancelling the shared connection here
        // could stop a different window's request after this one has finished.
        EventHandler<WindowEvent> ownerHidden = _ -> cancel();
        stateProperty().addListener((_, _, state) -> {
            if(state == State.SCHEDULED && owner != null) {
                owner.addEventHandler(WindowEvent.WINDOW_HIDDEN, ownerHidden);
                if(!owner.isShowing()) cancel();
            } else if(state == State.SUCCEEDED || state == State.FAILED || state == State.CANCELLED) {
                if(owner != null) owner.removeEventHandler(WindowEvent.WINDOW_HIDDEN, ownerHidden);
                pinDialog.cancelPinPrompt();
            }
        });
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
                            pinDialog);
                } catch(Exception e) {
                    throw new SignTransactionException(e.getMessage(), e);
                }
            }
        };
    }
}
