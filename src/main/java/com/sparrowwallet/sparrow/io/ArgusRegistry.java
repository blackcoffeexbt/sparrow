package com.sparrowwallet.sparrow.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.lark.ArgusClient;
import com.sparrowwallet.lark.argus.*;
import com.sparrowwallet.sparrow.AppServices;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Network/profile-scoped public pins, with independent identities in macOS Keychain. */
public final class ArgusRegistry {
    public record Pairing(String credentialId, String signer, List<String> relays, ArgusAccount account) {
        public Pairing {
            UUID.fromString(credentialId);
            new ArgusPairing(signer, "0".repeat(32), relays.stream().map(URI::create).toList());
            relays = List.copyOf(relays);
            Objects.requireNonNull(account);
        }
        public String path() { return "nostr:" + signer; }
        @Override public String toString() { return "Argus " + account.fingerprint() + " (" + signer.substring(0, 12) + "…)"; }
    }
    private static final ObjectMapper JSON = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
        .streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(65536).build()).build())
        .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final Map<String, ArgusClient> clients = new ConcurrentHashMap<>();
    private ArgusRegistry() {}
    public static ArgusNetwork network() {
        return switch(Network.get()) {
            case MAINNET -> ArgusNetwork.MAINNET;
            case TESTNET4 -> ArgusNetwork.TESTNET4;
            default -> throw new IllegalStateException("Argus supports Mainnet and Testnet4 only");
        };
    }
    private static Path file() { return Storage.getDataDir().toPath().resolve("argus.json"); }
    public static synchronized List<Pairing> pairings() {
        if(Network.get() != Network.MAINNET && Network.get() != Network.TESTNET4) return List.of();
        try {
            Path path = file(); if(!Files.exists(path)) return List.of();
            if(Files.size(path) > 65536) throw new IllegalStateException("Argus pairing file exceeds limit");
            Pairing[] records = JSON.readValue(Files.readAllBytes(path), Pairing[].class);
            if(records.length > 8) throw new IllegalStateException("Too many Argus pairings");
            Set<String> signers = new HashSet<>();
            for(Pairing p : records) if(p.account().network() != network() || !signers.add(p.signer())) throw new IllegalStateException("Invalid Argus network or duplicate signer");
            return List.of(records);
        } catch(Exception e) { throw new IllegalStateException("Cannot read Argus pairing pins. Restore the public pairing file before signing.", e); }
    }
    private static void save(List<Pairing> records) throws Exception {
        Path target = file(); Files.createDirectories(target.getParent());
        Path draft = Files.createTempFile(target.getParent(), "argus-public-", ".json");
        try {
            Files.write(draft, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(records));
            Files.move(draft, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(draft); }
    }
    public static synchronized void add(Pairing record, ArgusConnection connection) throws Exception {
        List<Pairing> records = new ArrayList<>(pairings());
        if(records.size() >= 8 || records.stream().anyMatch(p -> p.signer().equals(record.signer()))) throw new IllegalStateException("Argus is already paired, or the pairing list is full");
        records.add(record); save(records); clients.put(record.path(), new ArgusClient(connection));
    }
    public static List<Device> devices() {
        List<Device> result = new ArrayList<>();
        for(Pairing p : pairings()) {
            Device device = new Device(); device.setType("argus"); device.setPath(p.path()); device.setModel(WalletModel.ARGUS);
            device.setFingerprint(p.account().fingerprint()); device.setNeedsPinSent(false); device.setNeedsPassphraseSent(false);
            result.add(device);
        }
        return result;
    }
    private static void privacyCheck() {
        if(AppServices.isUsingProxy() || Config.get().requiresTor()) throw new IllegalStateException("Argus relay connections in this test build do not support Sparrow's Tor/SOCKS proxy. No direct relay connection was opened.");
    }
    public static ArgusConnection connect(byte[] identity, String signer, List<URI> urls, ArgusAccount pin) {
        privacyCheck();
        var transport = new ArgusRelayTransport(HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build(), urls, ArgusConnection.publicIdentity(identity), signer);
        return new ArgusConnection(identity, signer, network(), pin, transport);
    }
    public static synchronized ArgusClient client(Device device) {
        privacyCheck();
        String path = device.getPath();
        ArgusClient existing = clients.get(path); if(existing != null) return existing;
        Pairing pairing = pairings().stream().filter(p -> p.path().equals(path)).findFirst().orElseThrow(() -> new IllegalStateException("Argus pairing was removed"));
        byte[] identity = ArgusKeychain.load(pairing.credentialId());
        try {
            ArgusClient client = new ArgusClient(connect(identity, pairing.signer(), pairing.relays().stream().map(URI::create).toList(), pairing.account()));
            clients.put(path, client); return client;
        } finally { Arrays.fill(identity, (byte)0); }
    }
    public static synchronized void forget(Pairing pairing) throws Exception {
        ArgusClient client = clients.remove(pairing.path()); if(client != null) client.getConnection().close();
        List<Pairing> records = new ArrayList<>(pairings()); records.removeIf(p -> p.credentialId().equals(pairing.credentialId()));
        // Save removal first: a failed credential delete cannot leave an unusable active pairing.
        save(records); ArgusKeychain.delete(pairing.credentialId());
    }
    public static void cancelSigning(String path) { ArgusClient client = clients.get(path); if(client != null) client.getConnection().cancel(); }
    public static void close() { clients.values().forEach(c -> c.getConnection().close()); clients.clear(); }
}
