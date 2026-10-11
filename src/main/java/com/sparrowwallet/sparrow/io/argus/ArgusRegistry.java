package com.sparrowwallet.sparrow.io.argus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.lark.ArgusClient;
import com.sparrowwallet.lark.argus.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.Storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Network/profile-scoped public pins, with independent identities in macOS Keychain. */
public final class ArgusRegistry {
    private static volatile boolean stopped;
    private static final Logger log = LoggerFactory.getLogger(ArgusRegistry.class);

    public record Pairing(
            String credentialId, String signer, List<String> relays, ArgusAccount account) {
        public Pairing {
            UUID.fromString(credentialId);
            new ArgusPairing(signer, "0".repeat(32), relays.stream().map(URI::create).toList());
            relays = List.copyOf(relays);
            Objects.requireNonNull(account);
        }

        public String path() {
            return "nostr:" + signer;
        }

        @Override
        public String toString() {
            return "Argus " + account.fingerprint() + " (" + signer.substring(0, 12) + "…)";
        }
    }

    private static final ObjectMapper JSON =
            new ObjectMapper(
                            com.fasterxml.jackson.core.JsonFactory.builder()
                                    .streamReadConstraints(
                                            com.fasterxml.jackson.core.StreamReadConstraints
                                                    .builder()
                                                    .maxNestingDepth(16)
                                                    .maxStringLength(65536)
                                                    .build())
                                    .build())
                    .enable(
                            com.fasterxml.jackson.core.JsonParser.Feature
                                    .STRICT_DUPLICATE_DETECTION);
    private static final Map<String, ArgusConnectionHandle> clients = new ConcurrentHashMap<>();

    private ArgusRegistry() {}

    public static ArgusNetwork network() {
        return switch(Network.get()) {
            case MAINNET -> ArgusNetwork.MAINNET;
            case TESTNET4 -> ArgusNetwork.TESTNET4;
            default -> throw new IllegalStateException("Argus supports Mainnet and Testnet4 only");
        };
    }

    private static Path file() {
        return Storage.getDataDir().toPath().resolve("argus.json");
    }

    public static synchronized List<Pairing> pairings() {
        if(Network.get() != Network.MAINNET && Network.get() != Network.TESTNET4) return List.of();
        return loadPairings(file(), network());
    }

    static List<Pairing> loadPairings(Path path, ArgusNetwork network) {
        try {
            if(!Files.exists(path)) return List.of();
            byte[] data;
            try (var input = Files.newInputStream(path)) {
                data = input.readNBytes(65537);
            }
            if(data.length > 65536)
                throw new IllegalStateException("Argus pairing file exceeds limit");
            Pairing[] records = JSON.readValue(data, Pairing[].class);
            if(records.length > 8) throw new IllegalStateException("Too many Argus pairings");
            Set<String> signers = new HashSet<>();
            Set<String> credentials = new HashSet<>();
            for(Pairing pairing : records) {
                if(pairing.account().network() != network
                        || !signers.add(pairing.signer())
                        || !credentials.add(pairing.credentialId())) {
                    throw new IllegalStateException("Invalid Argus network or duplicate pairing");
                }
            }
            return List.of(records);
        } catch(Exception e) {
            throw new IllegalStateException(
                    "Cannot read Argus pairing pins. Restore the public pairing file before"
                            + " signing.",
                    e);
        }
    }

    private static void save(List<Pairing> records) throws Exception {
        Path target = file();
        Files.createDirectories(target.getParent());
        Path draft = Files.createTempFile(target.getParent(), "argus-public-", ".json");
        try {
            Files.write(draft, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(records));
            Files.move(
                    draft,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(draft);
        }
    }

    public static synchronized void add(Pairing record, ArgusConnection connection)
            throws Exception {
        if(stopped) throw new IllegalStateException("Argus connections are shutting down");
        List<Pairing> records = new ArrayList<>(pairings());
        if(records.size() >= 8
                || records.stream().anyMatch(p -> p.signer().equals(record.signer())))
            throw new IllegalStateException("Argus is already paired, or the pairing list is full");
        records.add(record);
        save(records);
        clients.put(record.path(), new ArgusConnectionHandle(new ArgusClient(connection)));
    }

    public static List<Device> devices() {
        List<Device> result = new ArrayList<>();
        List<Pairing> pairings;
        try {
            pairings = pairings();
        } catch(IllegalStateException e) {
            // Broken Argus pins fail closed without hiding unrelated USB/card devices.
            log.warn("Cannot enumerate Argus connections: restore the public pairing file");
            return result;
        }
        for(Pairing p : pairings) {
            Device device = new ArgusDevice(p);
            result.add(device);
        }
        return result;
    }

    private static void privacyCheck() {
        if(AppServices.isUsingProxy() || Config.get().requiresTor())
            throw new IllegalStateException(
                    "Argus relay connections do not support Sparrow's Tor/SOCKS proxy. No direct"
                            + " relay connection was opened.");
    }

    public static ArgusConnection connect(
            byte[] identity, String signer, List<URI> urls, ArgusAccount pin) {
        privacyCheck();
        var transport =
                new ArgusRelayTransport(
                        HttpClient.newBuilder()
                                .connectTimeout(java.time.Duration.ofSeconds(10))
                                .build(),
                        urls,
                        ArgusConnection.publicIdentity(identity),
                        signer);
        return new ArgusConnection(identity, signer, network(), pin, transport);
    }

    static synchronized ArgusConnectionHandle device(Device device) {
        if(stopped) throw new IllegalStateException("Argus connections are shutting down");
        privacyCheck();
        String path = device.getPath();
        Pairing pairing =
                pairings().stream()
                        .filter(p -> p.path().equals(path))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Argus pairing was removed"));
        ArgusConnectionHandle existing = clients.get(path);
        if(existing != null) return existing;
        byte[] identity = ArgusKeychain.load(pairing.credentialId());
        try {
            ArgusConnectionHandle client =
                    new ArgusConnectionHandle(
                            new ArgusClient(
                                    connect(
                                            identity,
                                            pairing.signer(),
                                            pairing.relays().stream().map(URI::create).toList(),
                                            pairing.account())));
            clients.put(path, client);
            return client;
        } finally {
            Arrays.fill(identity, (byte) 0);
        }
    }

    public static synchronized void forget(Pairing pairing) throws Exception {
        ArgusConnectionHandle client = clients.remove(pairing.path());
        if(client != null) client.close();
        List<Pairing> records = new ArrayList<>(pairings());
        records.removeIf(p -> p.credentialId().equals(pairing.credentialId()));
        // Save removal first: a failed credential delete cannot leave an unusable active pairing.
        save(records);
        ArgusKeychain.delete(pairing.credentialId());
    }

    public static synchronized void close() {
        stopped = true;
        clients.values().forEach(ArgusConnectionHandle::close);
        clients.clear();
    }
}
