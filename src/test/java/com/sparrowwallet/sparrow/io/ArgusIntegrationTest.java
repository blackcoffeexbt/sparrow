package com.sparrowwallet.sparrow.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.lark.argus.ArgusAccount;
import com.sparrowwallet.lark.argus.ArgusNetwork;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ArgusIntegrationTest {
    @Test void publicPairingRoundtripAndCapabilities() throws Exception {
        Network.set(Network.MAINNET);
        // Public fixture from the firmware/libwally interoperability tests, never a real wallet.
        String xpub = "xpub6DEHh42YXj7gdKbP1zxehEzQt47iW2AuUHYsqGaRxtkEGrK3bCYh2bsw1H6WUW26k9TBdQoe6gZ8ydoAP5eGAC2fDJGmFkwXcgv5feY9N7p";
        // Read the exact checksum from the shared public fixture instead of hand-maintaining it.
        var fixture = new ObjectMapper().readTree(getClass().getResourceAsStream("/argus/psbt-fixture.json"));
        ArgusAccount account = new ArgusAccount(xpub, "5a3469b6", fixture.path("descriptor").asText(), "e".repeat(32), ArgusNetwork.MAINNET, 150, true, true, true);
        var record = new ArgusRegistry.Pairing(UUID.randomUUID().toString(), "a".repeat(64), List.of("wss://relay.example"), account);
        ObjectMapper mapper = new ObjectMapper(); String encoded = mapper.writeValueAsString(record);
        assertFalse(encoded.contains("secret")); assertFalse(encoded.contains("\"pin\""));
        assertEquals(record, mapper.readValue(encoded, ArgusRegistry.Pairing.class));
        Device device = new Device(); device.setModel(WalletModel.ARGUS);
        assertTrue(device.supportsScriptType(ScriptType.P2WPKH));
        assertFalse(device.supportsScriptType(ScriptType.P2TR)); assertFalse(device.supportsScriptType(ScriptType.P2WSH));
        assertEquals("argus", WalletModel.ARGUS.getType()); assertTrue(WalletModel.ARGUS.alwaysIncludeNonWitnessUtxo());
        assertFalse(WalletModel.ARGUS.requiresPinPrompt());
        Wallet wallet = new Wallet(); wallet.setPolicyType(PolicyType.SINGLE_HD); wallet.setScriptType(ScriptType.P2WPKH);
        Keystore keystore = ArgusKeystore.keystore(wallet, null, record);
        assertEquals(KeystoreSource.HW_AIRGAPPED, keystore.getSource());
        assertEquals(WalletModel.ARGUS, keystore.getWalletModel());
        assertEquals(account.extendedKey(), keystore.getExtendedPublicKey());
        assertEquals("m/84'/0'/0'", keystore.getKeyDerivation().getDerivationPath());
        assertEquals(account.fingerprint(), keystore.getKeyDerivation().getMasterFingerprint());
        assertThrows(IllegalArgumentException.class, () -> ArgusKeystore.keystore(wallet, new KeyDerivation(account.fingerprint(), "m/84'/0'/1'"), record));
        wallet.setScriptType(ScriptType.P2TR);
        assertThrows(IllegalArgumentException.class, () -> ArgusKeystore.keystore(wallet, null, record));
        wallet.setScriptType(ScriptType.P2WPKH); wallet.setPolicyType(PolicyType.MULTI_HD);
        assertFalse(ArgusKeystore.supports(wallet));
        wallet.setPolicyType(PolicyType.SINGLE_HD);
        Network.set(Network.TESTNET4);
        try { assertThrows(IllegalArgumentException.class, () -> ArgusKeystore.keystore(wallet, null, record)); }
        finally { Network.set(Network.MAINNET); }
    }
    @Test void unsupportedNetworksAndPairingsFailClosed() {
        Network.set(Network.SIGNET);
        try { assertThrows(IllegalStateException.class, ArgusRegistry::network); assertTrue(ArgusRegistry.devices().isEmpty()); }
        finally { Network.set(Network.MAINNET); }
        assertThrows(Exception.class, () -> new ArgusRegistry.Pairing("not-a-uuid", "a".repeat(64), List.of("wss://relay.example"), null));
    }
}
