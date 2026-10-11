package com.sparrowwallet.sparrow.io.argus;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.control.ArgusImportPane;
import com.sparrowwallet.sparrow.control.TitledDescriptionPane;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.RemoteDeviceProvider;

import java.util.List;

public final class ArgusDeviceProvider implements RemoteDeviceProvider {
    @Override
    public WalletModel getWalletModel() {
        return WalletModel.ARGUS;
    }

    @Override
    public boolean isAvailable() {
        return ArgusKeychain.isAvailable()
                && (Network.get() == Network.MAINNET || Network.get() == Network.TESTNET4);
    }

    @Override
    public List<Device> enumerate() {
        return ArgusRegistry.devices();
    }

    @Override
    public TitledDescriptionPane createImportPane(Wallet wallet, KeyDerivation requiredDerivation) {
        return ArgusKeystore.supports(wallet)
                ? new ArgusImportPane(wallet, requiredDerivation)
                : null;
    }

    @Override
    public void close() {
        ArgusRegistry.close();
    }
}
