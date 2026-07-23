Place the Pumpkin server binaries here before building the APK.

Expected layout:
  arm64-v8a/libpumpkin.so   <- physical devices (aarch64)
  x86_64/libpumpkin.so      <- emulator (x86_64)

IMPORTANT: the file must be named "libpumpkin.so" (with the "lib" prefix
and ".so" extension) even though it is a regular ELF executable, not a
shared library. Android only extracts files from jniLibs/ that match this
naming convention, and only files extracted this way end up in an
executable location at runtime (see BinaryInstaller.java for details on
why files copied to getFilesDir() cannot be exec'd on Android 10+).

Run cross_compile.sh to build and place the binaries automatically.
