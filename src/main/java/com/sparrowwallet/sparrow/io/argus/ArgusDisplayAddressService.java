package com.sparrowwallet.sparrow.io.argus;

import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.DisplayAddressException;
import com.sparrowwallet.sparrow.io.Hwi;

import javafx.concurrent.Task;

final class ArgusDisplayAddressService extends Hwi.DisplayAddressService {
    private final Device device;
    private final String passphrase;
    private final OutputDescriptor descriptor;

    ArgusDisplayAddressService(
            Device device,
            String passphrase,
            ScriptType scriptType,
            OutputDescriptor addressDescriptor,
            OutputDescriptor walletDescriptor,
            String walletName,
            byte[] registration) {
        super(
                device,
                passphrase,
                scriptType,
                addressDescriptor,
                walletDescriptor,
                walletName,
                registration);
        this.device = device;
        this.passphrase = passphrase;
        this.descriptor = addressDescriptor;
    }

    @Override
    protected Task<String> createTask() {
        return new Task<>() {
            @Override
            protected String call() throws DisplayAddressException {
                try {
                    return ArgusApi.displayAddress(device, passphrase, descriptor);
                } catch(Exception e) {
                    throw new DisplayAddressException(e.getMessage(), e);
                }
            }
        };
    }
}
