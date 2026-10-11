package com.sparrowwallet.sparrow.io.argus;

import com.sparrowwallet.lark.argus.ArgusProgress;
import com.sparrowwallet.sparrow.control.ArgusPinDialog;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Run verifyArgusUi on a desktop. No credentials, relays or hardware. */
@Tag("argus-ui")
class ArgusSigningUiTest {
    @BeforeAll
    static void startToolkit() throws Exception {
        CompletableFuture<Void> started = new CompletableFuture<>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            started.complete(null);
        });
        started.get(10, TimeUnit.SECONDS);
    }

    @AfterAll
    static void stopToolkit() {
        Platform.exit();
    }

    @AfterEach
    void closeWindows() throws Exception {
        fx(() -> new ArrayList<>(Window.getWindows()).forEach(Window::hide));
    }

    private static void fx(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }

    private static Stage signingWindow() {
        Stage owner = new Stage();
        owner.setTitle("Synthetic Argus signing window");
        owner.setScene(new Scene(new StackPane(), 400, 220));
        owner.setAlwaysOnTop(true);
        owner.show();
        return owner;
    }

    @Test
    void hidingOwnerCancelsOnlyItsScheduledWorker() throws Exception {
        fx(() -> {
            Stage first = signingWindow(), second = signingWindow();
            var firstService = new ArgusSigningService(null, "", null, first);
            var secondService = new ArgusSigningService(null, "", null, second);
            // Hold the workers before they touch the registry, as a busy executor would.
            var queued = new ArrayList<Runnable>();
            firstService.setExecutor(queued::add);
            secondService.setExecutor(queued::add);
            firstService.start();
            secondService.start();
            assertEquals(2, queued.size());
            first.hide();
            assertEquals(Worker.State.CANCELLED, firstService.getState());
            assertEquals(Worker.State.SCHEDULED, secondService.getState());
            second.hide();
            assertEquals(Worker.State.CANCELLED, secondService.getState());
        });
    }

    @Test
    void hiddenOwnerCannotStartSigningOrOpenALatePinPrompt() throws Exception {
        var pin = new CompletableFuture<CompletableFuture<char[]>>();
        fx(() -> {
            Stage owner = signingWindow();
            owner.hide();
            var service = new ArgusSigningService(null, "", null, owner);
            service.setExecutor(_ -> {});
            service.start();
            assertEquals(Worker.State.CANCELLED, service.getState());
            pin.complete(new ArgusPinDialog(owner).requestPin(status()).toCompletableFuture());
        });
        assertNull(pin.get().get(10, TimeUnit.SECONDS));
        fx(() -> assertTrue(Window.getWindows().isEmpty()));
    }

    @Test
    void pinPromptIsOwnedModalAndClearedOnCancellation() throws Exception {
        var owner = new CompletableFuture<Stage>();
        var prompt = new CompletableFuture<ArgusPinDialog>();
        var pin = new CompletableFuture<CompletableFuture<char[]>>();
        fx(() -> {
            owner.complete(signingWindow());
            prompt.complete(new ArgusPinDialog(owner.join()));
            pin.complete(prompt.join().requestPin(status()).toCompletableFuture());
        });
        var input = new CompletableFuture<PasswordField>();
        var window = new CompletableFuture<Stage>();
        fx(() -> {
            Stage stage = (Stage)Window.getWindows().stream()
                    .filter(w -> w != owner.join()).findFirst().orElseThrow();
            window.complete(stage);
            assertSame(owner.join(), stage.getOwner());
            assertEquals(Modality.WINDOW_MODAL, stage.getModality());
            assertTrue(stage.isAlwaysOnTop());
            assertTrue(stage.isShowing());
            input.complete((PasswordField)stage.getScene().lookup(".password-field"));
            input.join().setText("123456"); // Synthetic, never submitted.
            prompt.join().cancelPinPrompt();
        });
        assertNull(pin.get().get(10, TimeUnit.SECONDS));
        fx(() -> {
            assertFalse(window.join().isShowing());
            assertEquals("", input.join().getText());
        });
    }

    @Test
    void pinPromptIsVisibleAboveAnOwnedSigningDialog() throws Exception {
        var signing = new CompletableFuture<Dialog<Void>>();
        var prompt = new CompletableFuture<ArgusPinDialog>();
        var pin = new CompletableFuture<CompletableFuture<char[]>>();
        fx(() -> {
            Stage wallet = signingWindow();
            Dialog<Void> signer = new Dialog<>();
            signer.setTitle("Synthetic owned signing dialog");
            signer.initOwner(wallet);
            signer.initModality(Modality.NONE);
            signer.getDialogPane().setContent(new StackPane());
            signer.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
            Stage owner = (Stage)signer.getDialogPane().getScene().getWindow();
            owner.setAlwaysOnTop(true);
            signer.show();
            signing.complete(signer);
            prompt.complete(new ArgusPinDialog(owner));
            pin.complete(prompt.join().requestPin(status()).toCompletableFuture());
        });
        fx(() -> {
            Stage stage = (Stage)Window.getWindows().stream()
                    .filter(w -> w instanceof Stage s && s.getTitle().equals("Unlock Argus for signing"))
                    .findFirst().orElseThrow();
            assertSame(signing.join().getDialogPane().getScene().getWindow(), stage.getOwner());
            assertTrue(stage.isShowing());
            assertTrue(stage.getWidth() > 0 && stage.getHeight() > 0);
            assertTrue(Double.isFinite(stage.getX()) && Double.isFinite(stage.getY()));
            prompt.join().cancelPinPrompt();
        });
        assertNull(pin.get().get(10, TimeUnit.SECONDS));
        fx(() -> signing.join().close());
    }

    @Test
    void cancellationBeforeUiDispatchDoesNotLeaveAPrompt() throws Exception {
        var pin = new CompletableFuture<CompletableFuture<char[]>>();
        fx(() -> {
            var prompt = new ArgusPinDialog(signingWindow());
            pin.complete(prompt.requestPin(status()).toCompletableFuture());
            prompt.cancelPinPrompt();
        });
        assertNull(pin.get().get(10, TimeUnit.SECONDS));
        fx(() -> assertEquals(1, Window.getWindows().size()));
    }

    private static ArgusProgress status() {
        return new ArgusProgress("PIN required", 2, "", 1800000150);
    }
}
