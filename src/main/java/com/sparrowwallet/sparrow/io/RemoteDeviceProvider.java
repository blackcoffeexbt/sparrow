package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.control.TitledDescriptionPane;

import java.util.List;

/** Discovery and onboarding for hardware reached outside USB or card readers. */
public interface RemoteDeviceProvider extends AutoCloseable {
    WalletModel getWalletModel();

    boolean isAvailable();

    List<Device> enumerate();

    TitledDescriptionPane createImportPane(Wallet wallet, KeyDerivation requiredDerivation);

    @Override
    void close();
}
