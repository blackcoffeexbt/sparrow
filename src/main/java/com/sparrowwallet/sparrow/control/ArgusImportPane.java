package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.lark.argus.ArgusPairing;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import org.controlsfx.glyphfont.Glyph;
import com.sparrowwallet.sparrow.event.KeystoreImportEvent;
import com.sparrowwallet.sparrow.io.ArgusRegistry;
import com.sparrowwallet.sparrow.io.ArgusKeystore;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** QR/file onboarding with an authenticated Nostr account, rather than a USB import. */
public final class ArgusImportPane extends TitledDescriptionPane {
    private final Wallet wallet;
    private final KeyDerivation requiredDerivation;

    public ArgusImportPane(Wallet wallet, KeyDerivation requiredDerivation) {
        super("Argus", "Key import", "Scan the pairing QR from Argus Settings > Connect Remote Client, or import a JSON file containing that QR. Approve pairing on Argus to import its native SegWit account. Signing and address verification use Nostr.", WalletModel.ARGUS);
        this.wallet = wallet;
        this.requiredDerivation = requiredDerivation;
        Button scan = new Button("Scan…");
        Glyph camera = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.CAMERA); camera.setFontSize(12); scan.setGraphic(camera);
        scan.setOnAction(_ -> new QRScanDialog().showAndWait().ifPresent(result -> {
            if(result.payload == null) setError("Incorrect QR", "Scan the Argus remote-client pairing QR.");
            else importPairing(result.payload);
        }));
        Button file = new Button("Import File…");
        file.setOnAction(_ -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Import Argus pairing JSON");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Argus pairing JSON", "*.json"));
            var selected = chooser.showOpenDialog(getScene().getWindow());
            if(selected == null) return;
            try(var input = Files.newInputStream(selected.toPath())) {
                byte[] bytes = input.readNBytes(65537);
                if(bytes.length > 65536) throw new IllegalArgumentException("Pairing file exceeds 64 KiB");
                importPairing(new String(bytes, StandardCharsets.UTF_8));
            } catch(Exception e) { setError("Could not import Argus", e.getMessage()); }
        });
        buttonBox.getChildren().addAll(scan, file);
        Button saved = new Button("Use saved connection…");
        saved.setOnAction(_ -> {
            try {
                var pairs = ArgusRegistry.pairings();
                if(pairs.isEmpty()) { setError("No saved connection", "Scan or import the pairing QR first."); return; }
                ChoiceDialog<ArgusRegistry.Pairing> choice = new ChoiceDialog<>(pairs.getFirst(), pairs);
                choice.setTitle("Import saved Argus"); choice.setHeaderText("Choose the paired Argus account");
                choice.initOwner(getScene().getWindow());
                choice.showAndWait().ifPresent(this::importAccount);
            } catch(Exception e) { setError("Could not import Argus", e.getMessage()); }
        });
        ((VBox)getContent()).getChildren().add(saved);
    }

    private void importPairing(String json) {
        try {
            ArgusPairing parsed = ArgusPairing.parse(json);
            var existing = ArgusRegistry.pairings().stream().filter(p -> p.signer().equals(parsed.signer())).findFirst();
            if(existing.isPresent()) { importAccount(existing.get()); return; }
            ArgusPairingDialog dialog = new ArgusPairingDialog(json);
            dialog.initOwner(getScene().getWindow());
            if(dialog.showAndWait().orElse(false)) {
                importAccount(ArgusRegistry.pairings().stream().filter(p -> p.signer().equals(parsed.signer())).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Argus pairing was not saved")));
            }
        } catch(Exception e) { setError("Could not import Argus", e.getMessage()); }
    }

    private void importAccount(ArgusRegistry.Pairing pairing) {
        try { EventManager.get().post(new KeystoreImportEvent(ArgusKeystore.keystore(wallet, requiredDerivation, pairing))); }
        catch(Exception e) { setError("Could not import Argus", e.getMessage()); }
    }

}
