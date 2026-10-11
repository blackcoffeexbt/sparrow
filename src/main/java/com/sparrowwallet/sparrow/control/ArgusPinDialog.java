package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.lark.argus.ArgusPinProvider;
import com.sparrowwallet.lark.argus.ArgusProgress;
import com.sparrowwallet.sparrow.AppServices;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

/** Async PIN entry. Relay reception continues while the user types or unlocks locally. */
public final class ArgusPinDialog implements ArgusPinProvider {
    private final AtomicLong generation = new AtomicLong();
    private Dialog<char[]> dialog;
    private PasswordField field;
    @Override public CompletionStage<char[]> requestPin(ArgusProgress status) {
        long token = generation.incrementAndGet();
        CompletableFuture<char[]> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            if(token != generation.get()) { future.complete(null); return; }
            if(dialog != null) { if(field != null) field.clear(); dialog.close(); }
            dialog = new Dialog<>(); dialog.setTitle("Unlock Argus for signing");
            AppServices.moveToActiveWindowScreen(dialog); AppServices.setStageIcon(dialog.getDialogPane().getScene().getWindow());
            field = new PasswordField(); field.setPromptText("Wallet PIN");
            field.setTextFormatter(new TextFormatter<String>(change -> change.getControlNewText().matches("[0-9]{0,32}") ? change : null));
            Label instructions = new Label("Enter the wallet PIN here, or on Argus. Then follow the device’s approval policy."); instructions.setWrapText(true);
            Label warning = new Label("A wrong PIN consumes a device attempt. Argus wipes its wallet after the 16th failed PIN attempt. Sparrow never saves this PIN."); warning.setWrapText(true);
            VBox box = new VBox(12, instructions, field, warning); box.setPrefWidth(410);
            dialog.getDialogPane().setContent(box);
            ButtonType unlock = new ButtonType("Unlock", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(unlock, ButtonType.CANCEL);
            Button submit = (Button)dialog.getDialogPane().lookupButton(unlock);
            submit.setDisable(true);
            field.textProperty().addListener((_, _, value) -> submit.setDisable(!value.matches("[0-9]{6,32}")));
            Dialog<char[]> current = dialog; PasswordField input = field;
            dialog.setResultConverter(button -> {
                char[] pin = button == unlock ? input.getText().toCharArray() : null;
                input.clear(); return pin;
            });
            dialog.setOnHidden(_ -> {
                input.clear();
                char[] result = current.getResult(); current.setResult(null);
                future.complete(result);
                if(dialog == current) { dialog = null; field = null; }
            });
            dialog.show(); field.requestFocus();
        });
        return future;
    }
    @Override public void cancelPinPrompt() {
        long token = generation.incrementAndGet();
        Platform.runLater(() -> {
            if(token != generation.get()) return;
            if(field != null) field.clear();
            if(dialog != null) { dialog.setResult(null); dialog.close(); }
        });
    }
}
