package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.PSBTSignedEvent;
import com.sparrowwallet.sparrow.io.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;

public class DeviceSignDialog extends DeviceDialog<PSBT> {
    private static final Logger log = LoggerFactory.getLogger(DeviceSignDialog.class);

    private final Wallet wallet;
    private final PSBT psbt;
    private final List<DevicePane> panes = new ArrayList<>();

    public DeviceSignDialog(Wallet wallet, List<String> operationFingerprints, PSBT psbt) {
        super(operationFingerprints);
        this.wallet = wallet;
        this.psbt = psbt;
        EventManager.get().register(this);
        setOnCloseRequest(event -> {
            EventManager.get().unregister(this);
            panes.forEach(DevicePane::cancelSigning);
        });
        setResultConverter(dialogButton -> dialogButton.getButtonData().isCancelButton() ? null : psbt);
    }

    @Override
    protected DevicePane getDevicePane(Device device, boolean defaultDevice) {
        DevicePane pane = new DevicePane(wallet, psbt, device, defaultDevice);
        panes.add(pane);
        return pane;
    }

    @Subscribe
    public void psbtSigned(PSBTSignedEvent event) {
        if(psbt == event.getPsbt()) {
            setResult(event.getSignedPsbt());
        }
    }
}
