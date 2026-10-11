package com.sparrowwallet.sparrow.io.argus;

import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.lark.ArgusClient;
import com.sparrowwallet.lark.Lark;
import com.sparrowwallet.lark.argus.ArgusPinProvider;
import com.sparrowwallet.lark.argus.ArgusProgress;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.net.ElectrumServer;

import java.util.Set;
import java.util.function.Consumer;

/** Sparrow's signing and address operations for a paired Argus device. */
public final class ArgusApi {
    private ArgusApi() {}

    private static Lark lark(ArgusClient client, String passphrase) {
        Lark lark = new Lark();
        lark.setPassphrase(passphrase);
        lark.addRemoteClient(client);
        return lark;
    }

    public static String displayAddress(
            Device device, String passphrase, OutputDescriptor descriptor) throws Exception {
        return ArgusRegistry.device(device)
                .execute(
                        client ->
                                lark(client, passphrase)
                                        .displayAddress(
                                                device.getType(), device.getPath(), descriptor));
    }

    public static PSBT signPSBT(
            Device device,
            String passphrase,
            PSBT psbt,
            Consumer<ArgusProgress> progress,
            ArgusPinProvider pinProvider)
            throws Exception {
        return ArgusRegistry.device(device)
                .execute(
                        client -> {
                            var connection = client.getConnection();
                            connection.setProgressListener(progress);
                            connection.setPinProvider(pinProvider);
                            client.setPreviousTransactions(ArgusApi::previousTransaction);
                            try {
                                return lark(client, passphrase)
                                        .signTransaction(device.getType(), device.getPath(), psbt);
                            } finally {
                                if(pinProvider != null) pinProvider.cancelPinPrompt();
                                connection.setPinProvider(null);
                                connection.setProgressListener(ignored -> {});
                                client.setPreviousTransactions(ignored -> null);
                            }
                        });
    }

    private static Transaction previousTransaction(Sha256Hash txid) {
        if(!AppServices.isConnected()) {
            return null;
        }
        try {
            var previous =
                    new ElectrumServer().getReferencedTransactions(Set.of(txid), null).get(txid);
            return previous == null ? null : previous.getTransaction();
        } catch(Exception e) {
            throw new IllegalStateException("Cannot retrieve the full previous transaction", e);
        }
    }
}
