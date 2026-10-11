package com.sparrowwallet.sparrow.io.argus;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.lark.argus.ArgusAccount;
import com.sparrowwallet.lark.argus.ArgusNetwork;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.RemoteDevices;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class ArgusRegistryTest {
    @TempDir Path directory;

    private ArgusRegistry.Pairing pairing(String credential, String signer) throws Exception {
        Network.set(Network.MAINNET);
        var fixture =
                new ObjectMapper()
                        .readTree(getClass().getResourceAsStream("/argus/psbt-fixture.json"));
        var account =
                new ArgusAccount(
                        fixture.path("xpub").asText(),
                        fixture.path("fingerprint").asText(),
                        fixture.path("descriptor").asText(),
                        "e".repeat(32),
                        ArgusNetwork.MAINNET,
                        150,
                        true,
                        true,
                        true);
        return new ArgusRegistry.Pairing(
                credential, signer, List.of("wss://relay.example"), account);
    }

    @Test
    void savedPinsRoundTripAndRejectNetworkOrCredentialReuse() throws Exception {
        Path file = directory.resolve("argus.json");
        String credential = UUID.randomUUID().toString();
        var first = pairing(credential, "a".repeat(64));
        ObjectMapper mapper = new ObjectMapper();
        Files.write(file, mapper.writeValueAsBytes(List.of(first)));
        assertEquals(List.of(first), ArgusRegistry.loadPairings(file, ArgusNetwork.MAINNET));
        assertThrows(
                IllegalStateException.class,
                () -> ArgusRegistry.loadPairings(file, ArgusNetwork.TESTNET4));
        Files.write(
                file,
                mapper.writeValueAsBytes(List.of(first, pairing(credential, "b".repeat(64)))));
        assertThrows(
                IllegalStateException.class,
                () -> ArgusRegistry.loadPairings(file, ArgusNetwork.MAINNET));
        Files.write(
                file,
                mapper.writeValueAsBytes(
                        List.of(first, pairing(UUID.randomUUID().toString(), first.signer()))));
        assertThrows(
                IllegalStateException.class,
                () -> ArgusRegistry.loadPairings(file, ArgusNetwork.MAINNET));
    }

    @Test
    @EnabledOnOs(OS.MAC)
    void savedDevicesSurviveLocalDiscoveryChangesAndDisappearWhenForgotten() throws Exception {
        String previousHome = System.getProperty("sparrow.home");
        Network previousNetwork = Network.get();
        System.setProperty("sparrow.home", directory.toString());
        try {
            var saved = pairing(UUID.randomUUID().toString(), "a".repeat(64));
            Path file = directory.resolve("argus.json");
            Files.write(file, new ObjectMapper().writeValueAsBytes(List.of(saved)));
            try(var executor = Executors.newSingleThreadExecutor()) {
                synchronized(ArgusRegistry.class) {
                    // Simulate a connection worker blocked in a Keychain approval.
                    assertEquals(List.of(saved), executor.submit(ArgusRegistry::pairings)
                            .get(5, TimeUnit.SECONDS));
                }
            }
            Device usb = new Device();
            usb.setModel(WalletModel.LEDGER_NANO_S);
            List<Device> local = List.of(usb);
            assertEquals(List.of(usb, new ArgusDevice(saved)), RemoteDevices.withLocalDevices(local));
            assertEquals(List.of(usb), local);
            // An empty USB snapshot must not remove a saved remote signer.
            assertEquals(List.of(new ArgusDevice(saved)), RemoteDevices.withLocalDevices(List.of()));
            assertTrue(AppServices.getDevices().contains(new ArgusDevice(saved)));
            Files.delete(file);
            assertEquals(local, RemoteDevices.withLocalDevices(local));
            assertFalse(AppServices.getDevices().contains(new ArgusDevice(saved)));
            // Broken public pins must not hide or poison local discovery.
            Files.writeString(file, "{invalid}");
            assertEquals(local, RemoteDevices.withLocalDevices(local));
        } finally {
            if(previousHome == null) System.clearProperty("sparrow.home");
            else System.setProperty("sparrow.home", previousHome);
            Network.set(previousNetwork);
        }
    }

    @Test
    void malformedAndOversizedPinsFailClosed() throws Exception {
        Path file = directory.resolve("argus.json");
        assertTrue(ArgusRegistry.loadPairings(file, ArgusNetwork.MAINNET).isEmpty());
        Files.writeString(file, "{invalid}");
        assertThrows(
                IllegalStateException.class,
                () -> ArgusRegistry.loadPairings(file, ArgusNetwork.MAINNET));
        Files.writeString(file, " ".repeat(65537));
        assertThrows(
                IllegalStateException.class,
                () -> ArgusRegistry.loadPairings(file, ArgusNetwork.MAINNET));
    }
}
