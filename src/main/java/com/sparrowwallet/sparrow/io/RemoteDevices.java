package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.control.TitledDescriptionPane;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/** Common remote-device hooks; providers own protocol, credentials and UI. */
public final class RemoteDevices {
    private static final List<RemoteDeviceProvider> providers =
            ServiceLoader.load(RemoteDeviceProvider.class).stream()
                    .map(ServiceLoader.Provider::get)
                    .toList();

    private RemoteDevices() {}

    public static List<Device> enumerate() {
        return providers.stream()
                .filter(RemoteDeviceProvider::isAvailable)
                .flatMap(provider -> provider.enumerate().stream())
                .toList();
    }

    /** Combine saved remote pairings with a USB/card snapshot without scanning hardware. */
    public static List<Device> withLocalDevices(List<Device> localDevices) {
        return Stream.concat(localDevices.stream(), enumerate().stream()).toList();
    }

    public static boolean supports(Keystore keystore) {
        return providers.stream()
                .anyMatch(
                        provider ->
                                provider.isAvailable()
                                        && provider.getWalletModel() == keystore.getWalletModel());
    }

    public static List<TitledDescriptionPane> getImportPanes(
            Wallet wallet, WalletModel requiredModel, KeyDerivation requiredDerivation) {
        return providers.stream()
                .filter(RemoteDeviceProvider::isAvailable)
                .filter(
                        provider ->
                                requiredModel == null || provider.getWalletModel() == requiredModel)
                .map(provider -> provider.createImportPane(wallet, requiredDerivation))
                .filter(Objects::nonNull)
                .toList();
    }

    public static void close() {
        providers.forEach(RemoteDeviceProvider::close);
    }
}
