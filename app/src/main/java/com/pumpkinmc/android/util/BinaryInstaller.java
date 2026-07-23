package com.pumpkinmc.android.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.io.File;

/**
 * Locates the Pumpkin binary that Android extracted into the app's
 * native library directory at install time.
 *
 * IMPORTANT: Since Android 10 (API 29), the OS enforces W^X (write XOR
 * execute) on an app's private files directory (getFilesDir()). A binary
 * copied there at runtime and marked with setExecutable(true) will still
 * fail with "Permission denied" (errno 13) when exec'd, because that
 * directory is mounted noexec / blocked by SELinux for regular files.
 *
 * The only location on modern Android where a bundled native binary is
 * both writable-by-nobody and executable-by-the-app is the directory
 * Android creates FROM jniLibs/ at install/update time:
 *   ApplicationInfo.nativeLibraryDir
 *   (e.g. /data/app/~~.../com.pumpkinmc.android-xxx/lib/arm64)
 *
 * For Android to extract a file from jniLibs/<abi>/ into that directory,
 * the file name must match the native library naming convention:
 *   lib<name>.so
 *
 * So the Pumpkin binary must be bundled as:
 *   app/src/main/jniLibs/arm64-v8a/libpumpkin.so
 *   app/src/main/jniLibs/x86_64/libpumpkin.so
 *
 * It is a regular ELF executable, not an actual shared library - only the
 * name and location matter to satisfy Android's packaging/extraction rules.
 */
public class BinaryInstaller {

    private static final String TAG = "BinaryInstaller";
    private static final String LIB_NAME = "libpumpkin.so";

    /**
     * Returns the Pumpkin executable already extracted by Android into the
     * app's native library directory. No copying is required and no
     * "Permission denied" issue occurs, since this directory is executable
     * by design (it holds real .so libraries too).
     *
     * @param context application context
     * @return the executable File, or {@code null} if not found
     */
    public static File locate(Context context) {
        ApplicationInfo appInfo = context.getApplicationInfo();
        String nativeLibDir = appInfo.nativeLibraryDir;

        if (nativeLibDir == null) {
            Log.e(TAG, "nativeLibraryDir is null - device/build configuration issue");
            return null;
        }

        File binary = new File(nativeLibDir, LIB_NAME);

        if (!binary.exists()) {
            Log.e(TAG, "Pumpkin binary not found at: " + binary.getAbsolutePath()
                    + "\nMake sure app/src/main/jniLibs/<abi>/" + LIB_NAME + " is bundled in the APK.");
            return null;
        }

        if (!binary.canExecute()) {
            // Should already be executable (Android sets this on extraction),
            // but attempt to fix it just in case.
            binary.setExecutable(true);
        }

        Log.i(TAG, "Using Pumpkin binary at: " + binary.getAbsolutePath());
        return binary;
    }
}
