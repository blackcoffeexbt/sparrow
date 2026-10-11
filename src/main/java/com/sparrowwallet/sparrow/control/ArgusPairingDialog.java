package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.lark.argus.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.UsbDeviceEvent;
import com.sparrowwallet.sparrow.io.*;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.event.ActionEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Device-approved pairing. The UI never requests Bitcoin seeds or wallet PINs here. */
public final class ArgusPairingDialog extends Dialog<Boolean> {
    private final TextArea qr = new TextArea();
    private final TextField label = new TextField("Sparrow");
    private final Label status = new Label();
    private final VBox saved = new VBox(8);
    private volatile ArgusConnection pending;
    private final AtomicBoolean stopped = new AtomicBoolean();
    private boolean pairing;
    public ArgusPairingDialog() { this(null); }
    public ArgusPairingDialog(String pairingJson) {
        setTitle("Argus connections");
        AppServices.moveToActiveWindowScreen(this); AppServices.setStageIcon(getDialogPane().getScene().getWindow());
        Label instructions = new Label("On Argus, open Settings > Connect Remote Client. Scan its QR or paste the QR’s JSON below. Sparrow and Argus must use the same Bitcoin network."); instructions.setWrapText(true);
        Label network = new Label("Bitcoin network: " + ArgusRegistry.network().wireName());
        if(pairingJson != null) qr.setText(pairingJson);
        qr.setPromptText("Argus pairing QR JSON"); qr.setPrefRowCount(4); qr.setWrapText(true);
        Button scan = new Button("Scan Argus QR…");
        scan.setOnAction(_ -> {
            QRScanDialog scanner = new QRScanDialog();
            scanner.showAndWait().ifPresent(result -> {
                if(result.payload != null) qr.setText(result.payload);
                else status.setText("Scan the pairing QR from Argus Settings.");
            });
        });
        status.setWrapText(true);
        refreshSaved();
        ScrollPane savedPane = new ScrollPane(saved); savedPane.setFitToWidth(true); savedPane.setPrefViewportHeight(120);
        VBox box = new VBox(12, instructions, network, scan, qr, new Label("Client name"), label, status, new Separator(), savedPane);
        box.setPrefWidth(510); getDialogPane().setContent(box);
        getDialogPane().getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        ButtonType pair = new ButtonType("Pair Argus", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(pair, ButtonType.CLOSE);
        Button pairButton = (Button)getDialogPane().lookupButton(pair);
        pairButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume(); if(pairing) return;
            try {
                ArgusPairing parsed = ArgusPairing.parse(qr.getText());
                if(ArgusRegistry.pairings().stream().anyMatch(p -> p.signer().equals(parsed.signer()))) throw new IllegalStateException("This Argus is already paired. Forget the saved connection before pairing it again.");
                String name = label.getText(); if(!name.matches("[\\x20-\\x7e]{1,40}")) throw new IllegalArgumentException("Use a 1–40 character plain-text client name");
                byte[] identity = ArgusConnection.newIdentity();
                String client = ArgusConnection.publicIdentity(identity), credential = UUID.randomUUID().toString();
                stopped.set(false); pairing = true; pairButton.setDisable(true); scan.setDisable(true); qr.clear(); qr.setDisable(true); label.setDisable(true);
                status.setText("Compare this client public key on Argus, then approve pairing on the device:\n" + client);
                Task<Boolean> task = new Task<>() {
                    @Override protected Boolean call() throws Exception {
                        AtomicBoolean stored = new AtomicBoolean(), registered = new AtomicBoolean();
                        ArgusConnection connection = null;
                        try {
                            ArgusKeychain.store(credential, identity); stored.set(true);
                            connection = ArgusRegistry.connect(identity, parsed.signer(), parsed.relays(), null); pending = connection;
                            if(stopped.get()) throw new InterruptedException("Pairing cancelled");
                            ArgusAccount account = connection.pair(parsed.token(), name);
                            if(stopped.get()) throw new InterruptedException("Pairing cancelled");
                            ArgusRegistry.add(new ArgusRegistry.Pairing(credential, parsed.signer(), parsed.relays().stream().map(Object::toString).toList(), account), connection);
                            registered.set(true); return true;
                        } finally {
                            Arrays.fill(identity, (byte)0); pending = null;
                            if(!registered.get()) {
                                if(connection != null) connection.close();
                                if(stored.get()) ArgusKeychain.delete(credential);
                            }
                        }
                    }
                };
                task.setOnSucceeded(_ -> { pairing = false; publishDevices(); setResult(true); close(); });
                task.setOnFailed(_ -> {
                    pairing = false; pairButton.setDisable(false); scan.setDisable(false); qr.setDisable(false); label.setDisable(false);
                    status.setText("Pairing failed: " + task.getException().getMessage() + ". If Argus already approved this client, revoke it on the device before retrying.");
                });
                Thread worker = new Thread(task, "argus-pair"); worker.setDaemon(true); worker.start();
            } catch(Exception e) { status.setText(e.getMessage()); }
        });
        setOnHidden(_ -> { stopped.set(true); qr.clear(); if(pending != null) pending.cancel(); });
        setResultConverter(button -> false);
    }
    private void refreshSaved() {
        saved.getChildren().clear(); saved.getChildren().add(new Label("Saved Argus connections"));
        for(ArgusRegistry.Pairing p : ArgusRegistry.pairings()) {
            Label info = new Label("Fingerprint: " + p.account().fingerprint() + "\nSigner: " + p.signer()); info.setWrapText(true);
            Button forget = new Button("Forget connection");
            forget.setOnAction(_ -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Remove Sparrow’s saved connection and Keychain credential? This does not cancel signing or revoke Sparrow on Argus. Revoke the client in Argus Settings too.", ButtonType.OK, ButtonType.CANCEL);
                if(confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
                forget.setDisable(true);
                Task<Void> task = new Task<>() { @Override protected Void call() throws Exception { ArgusRegistry.forget(p); return null; } };
                task.setOnSucceeded(event -> { refreshSaved(); publishDevices(); });
                task.setOnFailed(event -> { forget.setDisable(false); status.setText(task.getException().getMessage()); });
                Thread worker = new Thread(task, "argus-forget"); worker.setDaemon(true); worker.start();
            });
            saved.getChildren().addAll(info, forget);
        }
    }
    public static void publishDevices() {
        var existing = AppServices.getDevices();
        List<Device> devices = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        devices.removeIf(d -> "argus".equals(d.getType())); devices.addAll(ArgusRegistry.devices());
        EventManager.get().post(new UsbDeviceEvent(devices));
    }
}
