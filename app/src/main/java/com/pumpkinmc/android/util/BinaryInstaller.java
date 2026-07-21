package com.pumpkinmc.android.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Copies the Pumpkin binary bundled in assets/ into the app's private storage
 * and keeps it up to date across app updates.
 *
 * Expected asset layout:
 *   assets/pumpkin/
 *     ├── arm64-v8a/pumpkin    <- physical devices (aarch64)
 *     └── x86_64/pumpkin       <- emulator (x86_64)
 */
public class BinaryInstaller {

    private static final String TAG = "BinaryInstaller";
    private static final String PREF_NAME = "pumpkin_install";
    private static final String PREF_INSTALLED_VERSION = "installed_version";
    private static final String BINARY_NAME = "pumpkin";

    /**
     * Installs the binary (only re-copies when the app version has changed)
     * and returns a {@link File} pointing to the executable.
     *
     * @param context   application context
     * @param serverDir destination directory for server data
     * @return executable binary file, or {@code null} on failure
     */
    public static File install(Context context, File serverDir) {
        File binDir = new File(serverDir, "bin");
        if (!binDir.exists()) binDir.mkdirs();

        File binary = new File(binDir, BINARY_NAME);
        String abi = getPrimaryAbi();
        String assetPath = "pumpkin/" + abi + "/" + BINARY_NAME;

        // Skip copy if the binary is already up to date for this app version
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int installedVersion = prefs.getInt(PREF_INSTALLED_VERSION, -1);
        int currentVersion = getVersionCode(context);

        if (binary.exists() && installedVersion == currentVersion) {
            Log.d(TAG, "Binary is up to date (v" + currentVersion + ")");
            binary.setExecutable(true);
            return binary;
        }

        Log.i(TAG, "Installing binary: " + assetPath + " -> " + binary.getAbsolutePath());

        try (InputStream in = context.getAssets().open(assetPath);
             OutputStream out = new FileOutputStream(binary)) {

            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();

        } catch (IOException e) {
            Log.e(TAG, "Failed to copy binary from assets: " + assetPath, e);
            // Try the other ABI as a fallback
            return tryFallbackAbi(context, binDir, binary, abi);
        }

        binary.setExecutable(true, false); // owner-executable
        prefs.edit().putInt(PREF_INSTALLED_VERSION, currentVersion).apply();
        Log.i(TAG, "Installation complete: " + binary.getAbsolutePath());
        return binary;
    }

    /**
     * Returns the best ABI string supported by this device.
     * Pumpkin supports aarch64 (arm64-v8a) and x86_64.
     */
    public static String getPrimaryAbi() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if (abi.equals("arm64-v8a") || abi.equals("x86_64")) {
                return abi;
            }
        }
        // Fallback – shouldn't happen on any modern Android device
        return Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "arm64-v8a";
    }

    /** Tries the alternate ABI when the primary one is not bundled. */
    private static File tryFallbackAbi(Context context, File binDir, File binary, String primaryAbi) {
        String fallback = primaryAbi.equals("arm64-v8a") ? "x86_64" : "arm64-v8a";
        String assetPath = "pumpkin/" + fallback + "/" + BINARY_NAME;
        Log.w(TAG, "Trying fallback ABI: " + fallback);
        try (InputStream in = context.getAssets().open(assetPath);
             OutputStream out = new FileOutputStream(binary)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            binary.setExecutable(true, false);
            return binary;
        } catch (IOException e2) {
            Log.e(TAG, "Fallback ABI also failed", e2);
            return null;
        }
    }

    private static int getVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 1;
        }
    }
}
