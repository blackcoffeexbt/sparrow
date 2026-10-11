package com.sparrowwallet.sparrow.io.argus;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparrowwallet.lark.ArgusClient;
import com.sparrowwallet.lark.DeviceException;
import com.sparrowwallet.lark.argus.ArgusAccount;
import com.sparrowwallet.lark.argus.ArgusConnection;
import com.sparrowwallet.lark.argus.ArgusNetwork;
import com.sparrowwallet.lark.argus.ArgusTransport;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class ArgusDeviceTest {
    private ArgusDevice device() throws Exception {
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
        byte[] identity = new byte[32];
        identity[31] = 3;
        byte[] signer = new byte[32];
        signer[31] = 4;
        ArgusTransport transport =
                new ArgusTransport() {
                    @Override
                    public void publish(com.fasterxml.jackson.databind.JsonNode event) {
                        fail("No relay access expected");
                    }

                    @Override
                    public com.fasterxml.jackson.databind.JsonNode receive(Duration timeout) {
                        throw new AssertionError("No relay access expected");
                    }

                    @Override
                    public void close() {}
                };
        return new ArgusDevice(
                new ArgusClient(
                        new ArgusConnection(
                                identity,
                                ArgusConnection.publicIdentity(signer),
                                ArgusNetwork.MAINNET,
                                account,
                                transport)));
    }

    @Test
    void rejectsOverlappingOperationsWithoutRunningTheirCallback() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var device = device();
                var executor = Executors.newSingleThreadExecutor()) {
            var running =
                    executor.submit(
                            () ->
                                    device.execute(
                                            client -> {
                                                started.countDown();
                                                assertTrue(release.await(5, TimeUnit.SECONDS));
                                                return "first result";
                                            }));
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(
                        DeviceException.class,
                        () ->
                                device.execute(
                                        client -> {
                                            fail(
                                                    "A competing operation must not replace the"
                                                        + " first operation's callbacks");
                                            return null;
                                        }));
            } finally {
                release.countDown();
            }
            assertEquals("first result", running.get(5, TimeUnit.SECONDS));
            assertEquals("next result", device.execute(client -> "next result"));
        }
    }

    @Test
    void failureReleasesOperationAndClosedDeviceRejectsFurtherWork() throws Exception {
        try (var device = device()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            device.execute(
                                    client -> {
                                        throw new IllegalArgumentException(
                                                "Synthetic validation failure");
                                    }));
            assertEquals("recovered", device.execute(client -> "recovered"));
            device.close();
            assertThrows(DeviceException.class, () -> device.execute(client -> "unexpected"));
        }
    }
}
