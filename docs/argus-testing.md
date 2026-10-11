# Argus macOS test build

This fork adds the existing single native SegWit Argus account on Mainnet and
Testnet4, using Nostr. It includes the modified independent Lark and Drongo
checkouts. Bitcoin keys and approval policy stay on the ESP32. Broadcasting still
uses Sparrow's separate transaction review and Broadcast action.

## Start with a separate profile

Use `Launch Argus Test.command` beside the test app, or launch its executable with
an explicit home folder:

```sh
Sparrow.app/Contents/MacOS/Sparrow -d /absolute/path/to/argus-test-home -n testnet4
```

The launcher uses a new `argus-test-home` next to itself, opens Testnet4, and starts
a fresh profile offline. It does not read the normal Sparrow profile. Match
the network to the **installed** Argus firmware: firmware network is build-time
only. For Mainnet, use `-n mainnet` with a separate profile, or restart the test app
in Mainnet through Tools. Pair independently for each network.

This is a local ad hoc signed development build, not an official Sparrow release.
It is not notarized. Do not replace your installed Sparrow app or copy real
wallet files into its test profile for the initial acceptance checks.

## Pair and import

1. Create a single-signature **Native SegWit** wallet in Sparrow.
2. In its keystore choose **Airgapped Hardware Wallet > Argus**.
3. On Argus, open **Settings > Connect Remote Client**. In Sparrow click
   **Scan…**, or **Import File…** with a UTF-8 JSON file containing that pairing
   QR's exact contents. The QR/token expires after three minutes. This is a
   connection invitation, not a seed or a transaction file.
4. Click **Pair Argus**. Compare the displayed **client public key** on the
   device and approve pairing locally. Sparrow imports the authenticated account
   automatically. No wallet PIN is needed for pairing.
5. For an already paired Argus, choose **Use saved connection…** in the Argus
   entry's Details. Only account zero is supported: `m/84'/0'/0'` on Mainnet,
   `m/84'/1'/0'` on Testnet4. **Manage connections…** in the Argus entry's Details manages saved pairs.
6. Verify a receive address on Argus before funding. The device shows the network,
   requester, path and derived address; compare it before confirming.

The keystore stays in the Airgapped Hardware Wallet category. Signing and address
verification use its paired Nostr connection; no USB connection is required.

Sparrow saves only public account pins and relay settings in the network-specific
`argus.json`. Its independent Nostr identity is a generic-password entry in
**macOS Keychain**, under service `com.sparrowwallet.argus.nostr`. Keychain may
request your approval to allow this development app to access its own entry.
There is no plaintext credential fallback. Never paste a Bitcoin seed or PIN into
the pairing JSON field. Forgetting a connection removes its local pin/credential;
revoke that client in Argus Settings too.

## Sign with a PIN in Sparrow or on Argus

Build a supported transaction and click **Sign**. Only after an authenticated,
request-bound **PIN required** status does Sparrow display **Unlock Argus for
signing**. Enter 6–32 digits there, or use the keypad on Argus. Local PIN progress
closes the Sparrow prompt. The PIN travels inside the existing v2 ephemeral
AES-GCM session and signed NIP-44 envelope; it is never saved or logged. Mutable
PIN/session buffers are cleared after use. JVM/JCE/GUI string copies limit complete
historical memory erasure; heap dumps are disabled in this test build.

PIN entry does not bypass local review, spending limits, deny rules or paired
approval quorums. The final signed PSBT is independently checked against the
original transaction and every signature verified before success is reported.
Review the final transaction and explicitly choose Broadcast separately.

A wrong PIN consumes a persistent device attempt; the 16th failure wipes the
wallet and other device settings. Sparrow shows the bound error/remaining count
and never retries PIN verification automatically. Cancellation closes the prompt
and stops waiting; a request already delivered can still sign until its deadline.
Inspect Argus before retrying. Delivery retries reuse identical ciphertext.

## Required firmware and current limits

Use firmware from `feat/argus-sparrow` for Sparrow's normal RBF/block-height
locktime transactions and on-device address verification. Older v2 firmware can
pair, but reports an upgrade-required error for those features. No firmware was
flashed by this source/build work.

Supported: host PSBT v0/v2 (a separate PSBT v0 copy is sent to firmware;
Sparrow's original version, transaction and metadata are preserved), transaction
version 2, BIP84 account zero, receive/change,
`SIGHASH_ALL`, full previous transactions, at most 32 inputs/outputs and the device's
32 KiB/secure-response limits. Sparrow includes full previous transactions and
can fetch missing ones through its configured chain backend, then verifies their
hashes. No Taproot recipients/inputs, multisig inputs, other accounts, passphrases,
message signing, relative/time-based timelocks or silent payments are supported.
Fee replacements count their full debit again under the device's existing
no-refund spending policy.

The macOS integration uses direct `wss://` relay connections. It **refuses
Argus operations while Sparrow's Tor/SOCKS proxy is active**, rather than sending
around it. Proxy support and non-macOS credential storage remain future work.
Other paired companion clients can supply quorum approvals; this build does not
add an Argus approver UI.

## Acceptance checklist

- Pair/reconnect/restart and confirm the same account/fingerprint.
- Match device/Sparrow receive and change addresses; reject one address request.
- Sign a small Testnet4 payment with the PIN in Sparrow, then with local PIN entry.
- Confirm device manual/policy/quorum approval still applies.
- Cancel the prompt, reject on-device, disconnect a relay, and let a request expire.
- Confirm duplicate delivery never repeats PIN attempts or spending reservations.
- Verify normal payment, send-max, consolidation, RBF and block-height locktime.
- Verify network/account mismatch, unsupported recipients and oversized PSBTs fail.
- Confirm signing does not broadcast. A Testnet4 broadcast requires your separate
  explicit authorization during manual testing.

Automated checks use synthetic identities, PINs and PSBTs. They do not access real
Keychain credentials, devices, relays or broadcasters. A successful build is not
physical interoperability verification. A local Testnet4 pairing and signing
round trip was confirmed by the tester; broader device and failure-path
acceptance checks still need to be completed.

## Rebuild

Keep all repositories independent; no local repositories are nested or added as
submodules. Upstream's dependency gitlinks are unchanged. Supply their paths:

```sh
./gradlew -PdrongoDir=../drongo -PlarkDir=../lark :test
./gradlew -PdrongoDir=../drongo -PlarkDir=../lark jpackageImage
```

Java 25 or newer is required. The bundle is `build/jpackage/Sparrow.app`.

## Code structure and review boundaries

`io/argus/` contains the Sparrow-specific Argus API, paired-device operation
lock, public pairing registry, keystore conversion and macOS Keychain adapter.
`control/Argus*` contains only onboarding/PIN UI. The shared device UI asks each
`Device` for its signing/address service; the USB default remains `Hwi`, while `ArgusDevice` selects its own services and
`ArgusApi`. `Hwi.signPSBT()` and `Hwi.SignPSBTService` contain no Argus branches
or PIN/progress arguments. Transport, session cryptography and strict PSBT checks
live in Lark.
ArgusClient remains beside Lark's other HardwareClient implementations.

The paired-device lock covers callbacks, previous-transaction lookup, account
refresh and signing together. Competing operations fail before replacing an
active request's PIN/progress callbacks. Closing a signing dialog interrupts only
its own worker; it cannot cancel another window's request. The PIN dialog is owned
by that signing window, is window-modal, and inherits its always-on-top setting.

Drongo's `WalletModel.ARGUS` addition is a prerequisite for the Lark and Sparrow
changes. Keep upstream dependency gitlinks untouched until compatible revisions
are available. Local `drongoDir`/`larkDir` Gradle overrides support independent
checkouts during review. All source repositories remain independent.

Known review limits: Sparrow's credential adapter is macOS-only and the Argus
import entry is hidden on other platforms. Tor/SOCKS relay routing is unsupported
and fails closed. Lark accepts an injected HttpClient/transport but cannot enforce
an application's proxy policy itself. These boundaries are explicit; portable
credential storage, proxied relay support and an independent cryptographic review
are separate follow-up work.

Remote discovery and keystore onboarding use the small `RemoteDeviceProvider`
service registration. `RemoteDevices` supplies shared enumeration, wallet
capability checks, import panes and shutdown without vendor-specific branches.
The Argus provider is registered in the module descriptor and in META-INF/services
for classpath builds. BitBox, Ledger and Coldcard retain their existing Lark/HWI
paths unchanged. Argus has no top-level Tools menu; its connection management
belongs to the Argus keystore import entry.
