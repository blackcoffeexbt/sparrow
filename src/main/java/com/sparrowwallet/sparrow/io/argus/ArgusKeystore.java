package com.sparrowwallet.sparrow.io.argus;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.*;

/** Build a keystore only from the authenticated public account saved during pairing. */
public final class ArgusKeystore {
    private ArgusKeystore() {}

    public static boolean supports(Wallet wallet) {
        return wallet.getPolicyType() == PolicyType.SINGLE_HD
                && wallet.getScriptType() == ScriptType.P2WPKH
                && (Network.get() == Network.MAINNET || Network.get() == Network.TESTNET4);
    }

    public static Keystore keystore(
            Wallet wallet, KeyDerivation required, ArgusRegistry.Pairing pairing) {
        if(!supports(wallet) || pairing.account().network() != ArgusRegistry.network())
            throw new IllegalArgumentException(
                    "Argus requires a matching network and single native SegWit account");
        KeyDerivation derivation =
                new KeyDerivation(
                        pairing.account().fingerprint(), pairing.account().network().accountPath());
        if(required != null && !required.getDerivation().equals(derivation.getDerivation()))
            throw new IllegalArgumentException(
                    "Argus supports account zero only: " + derivation.getDerivationPath());
        Keystore keystore = new Keystore();
        keystore.setLabel("Argus");
        keystore.setWalletModel(WalletModel.ARGUS);
        keystore.setSource(KeystoreSource.HW_AIRGAPPED);
        keystore.setKeyDerivation(derivation);
        keystore.setExtendedPublicKey(pairing.account().extendedKey());
        return keystore;
    }
}
