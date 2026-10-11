package com.sparrowwallet.sparrow.io.argus;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.lark.argus.ArgusAccount;
import com.sparrowwallet.lark.argus.ArgusNetwork;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

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
