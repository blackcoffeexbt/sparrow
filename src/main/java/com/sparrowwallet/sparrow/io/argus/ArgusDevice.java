package com.sparrowwallet.sparrow.io.argus;

import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.DeviceDisplayAddressService;
import com.sparrowwallet.sparrow.io.DeviceSigningService;

import javafx.stage.Window;

/** Selects Argus services without adding protocol branches to the shared HWI UI. */
public final class ArgusDevice extends Device {
    public ArgusDevice(ArgusRegistry.Pairing pairing) {
        setType("argus");
        setPath(pairing.path());
        setModel(WalletModel.ARGUS);
        setFingerprint(pairing.account().fingerprint());
        setNeedsPinSent(false);
        setNeedsPassphraseSent(false);
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    @Override
    public boolean supportsScriptType(ScriptType scriptType) {
        return scriptType == ScriptType.P2WPKH;
    }

    @Override
    public DeviceSigningService createSigningService(
            String passphrase,
            PSBT psbt,
            OutputDescriptor descriptor,
            String walletName,
            byte[] registration,
            Window owner) {
        return new ArgusSigningService(this, passphrase, psbt, owner);
    }

    @Override
    public DeviceDisplayAddressService createDisplayAddressService(
            String passphrase,
            ScriptType scriptType,
            OutputDescriptor addressDescriptor,
            OutputDescriptor walletDescriptor,
            String walletName,
            byte[] registration) {
        return new ArgusDisplayAddressService(this, passphrase, addressDescriptor);
    }
}
