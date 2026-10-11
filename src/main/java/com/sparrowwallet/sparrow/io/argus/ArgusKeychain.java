package com.sparrowwallet.sparrow.io.argus;

import com.sparrowwallet.drongo.OsType;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.nio.charset.StandardCharsets;

/** macOS generic-password Keychain entries; secret bytes never enter process arguments or files. */
public final class ArgusKeychain {
    private static final String SERVICE = "com.sparrowwallet.argus.nostr";
    private static final int NOT_FOUND = -25300;

    private interface Security extends Library {
        int SecKeychainAddGenericPassword(
                Pointer keychain,
                int serviceLength,
                byte[] service,
                int accountLength,
                byte[] account,
                int passwordLength,
                byte[] password,
                PointerByReference item);

        int SecKeychainFindGenericPassword(
                Pointer keychain,
                int serviceLength,
                byte[] service,
                int accountLength,
                byte[] account,
                IntByReference length,
                PointerByReference password,
                PointerByReference item);

        int SecKeychainItemFreeContent(Pointer attributes, Pointer data);

        int SecKeychainItemDelete(Pointer item);
    }

    private interface CoreFoundation extends Library {
        void CFRelease(Pointer pointer);
    }

    private static final class Frameworks {
        static final Security SECURITY = Native.load("Security", Security.class);
        static final CoreFoundation CORE = Native.load("CoreFoundation", CoreFoundation.class);
    }

    public static boolean isAvailable() {
        return OsType.getCurrent() == OsType.MACOS;
    }

    private static void available() {
        if(!isAvailable()) throw new IllegalStateException("Argus requires macOS Keychain");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static void store(String credentialId, byte[] secret) {
        available();
        if(secret.length != 32) throw new IllegalArgumentException("Invalid Nostr identity");
        byte[] service = bytes(SERVICE), account = bytes(credentialId);
        int result =
                Frameworks.SECURITY.SecKeychainAddGenericPassword(
                        null,
                        service.length,
                        service,
                        account.length,
                        account,
                        secret.length,
                        secret,
                        null);
        if(result != 0)
            throw new IllegalStateException(
                    "Cannot save Argus identity in macOS Keychain (status " + result + ")");
    }

    public static byte[] load(String credentialId) {
        available();
        byte[] service = bytes(SERVICE), account = bytes(credentialId);
        IntByReference size = new IntByReference();
        PointerByReference data = new PointerByReference();
        int result =
                Frameworks.SECURITY.SecKeychainFindGenericPassword(
                        null, service.length, service, account.length, account, size, data, null);
        if(result != 0)
            throw new IllegalStateException(
                    "Cannot unlock Argus identity in macOS Keychain (status " + result + ")");
        try {
            if(size.getValue() != 32)
                throw new IllegalStateException("Invalid Argus credential length");
            return data.getValue().getByteArray(0, 32);
        } finally {
            if(data.getValue() != null) {
                data.getValue().clear(Math.max(0, size.getValue()));
                Frameworks.SECURITY.SecKeychainItemFreeContent(null, data.getValue());
            }
        }
    }

    public static void delete(String credentialId) {
        available();
        byte[] service = bytes(SERVICE), account = bytes(credentialId);
        PointerByReference item = new PointerByReference();
        int result =
                Frameworks.SECURITY.SecKeychainFindGenericPassword(
                        null, service.length, service, account.length, account, null, null, item);
        if(result == NOT_FOUND) return;
        if(result != 0)
            throw new IllegalStateException(
                    "Cannot remove Argus credential (status " + result + ")");
        try {
            if(Frameworks.SECURITY.SecKeychainItemDelete(item.getValue()) != 0)
                throw new IllegalStateException("Cannot remove Argus credential");
        } finally {
            Frameworks.CORE.CFRelease(item.getValue());
        }
    }
}
