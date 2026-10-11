package com.sparrowwallet.sparrow.io.argus;

import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.DeviceService;
import com.sparrowwallet.sparrow.io.DisplayAddressException;

import javafx.concurrent.Task;

final class ArgusDisplayAddressService extends DeviceService<String> {
    private final Device device;
    private final String passphrase;
    private final OutputDescriptor descriptor;

    ArgusDisplayAddressService(
            Device device, String passphrase, OutputDescriptor addressDescriptor) {
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
