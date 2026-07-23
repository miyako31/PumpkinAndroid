#!/usr/bin/env bash
# =============================================================================
# cross_compile.sh – Cross-compile Pumpkin for Android
#
# Host requirements:
#   - Rust (rustup)         https://rustup.rs
#   - Android NDK r26+      set ANDROID_NDK_HOME before running
#   - cargo-ndk             installed automatically
#   - git
#
# Usage:
#   export ANDROID_NDK_HOME=/path/to/android-ndk-r26
#   chmod +x cross_compile.sh
#   ./cross_compile.sh
# =============================================================================

set -euo pipefail

# --- Configuration ---
PUMPKIN_REPO="https://github.com/Pumpkin-MC/Pumpkin.git"
PUMPKIN_DIR="./Pumpkin"
OUTPUT_DIR="./app/src/main/jniLibs"
MIN_SDK=26

# Targets to build: "<rust_triple>:<android_abi>"
TARGETS=(
    "aarch64-linux-android:arm64-v8a"   # physical devices (ARM64)
    "x86_64-linux-android:x86_64"       # emulator
)

# --- Coloured output helpers ---
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# --- Preflight checks ---
check_requirements() {
    info "Checking requirements..."

    command -v rustup >/dev/null 2>&1 || error "rustup not found. Install it from https://rustup.rs"
    command -v cargo  >/dev/null 2>&1 || error "cargo not found"
    command -v git    >/dev/null 2>&1 || error "git not found"

    if [ -z "${ANDROID_NDK_HOME:-}" ]; then
        error "ANDROID_NDK_HOME is not set.
Example: export ANDROID_NDK_HOME=\$HOME/Android/Sdk/ndk/26.1.10909125"
    fi

    [ -d "$ANDROID_NDK_HOME" ] || error "ANDROID_NDK_HOME does not exist: $ANDROID_NDK_HOME"

    info "NDK: $ANDROID_NDK_HOME"
}

# --- Install Rust targets ---
install_targets() {
    info "Adding Rust targets..."
    for pair in "${TARGETS[@]}"; do
        target="${pair%%:*}"
        rustup target add "$target"
        info "  + $target"
    done
}

# --- Install cargo-ndk ---
install_cargo_ndk() {
    if ! command -v cargo-ndk >/dev/null 2>&1; then
        info "Installing cargo-ndk..."
        cargo install cargo-ndk
    else
        info "cargo-ndk already installed"
    fi
}

# --- Clone / update Pumpkin ---
clone_pumpkin() {
    if [ -d "$PUMPKIN_DIR" ]; then
        info "Updating Pumpkin repository..."
        cd "$PUMPKIN_DIR"
        git pull
        git submodule update --init --recursive
        cd ..
    else
        info "Cloning Pumpkin (with submodules)..."
        git clone --recurse-submodules --depth=1 --shallow-submodules             "$PUMPKIN_REPO" "$PUMPKIN_DIR"
    fi
}

# --- Build ---
build_for_android() {
    info "Building Pumpkin for Android..."
    mkdir -p "$OUTPUT_DIR"

    cd "$PUMPKIN_DIR"

    for pair in "${TARGETS[@]}"; do
        target="${pair%%:*}"
        abi="${pair##*:}"

        info "Building $target ($abi)..."

        cargo ndk \
            --target "$target" \
            --platform "$MIN_SDK" \
            build --release -p pumpkin

        # Locate the output binary
        src="target/${target}/release/pumpkin"

        if [ -f "$src" ]; then
            dst="../$OUTPUT_DIR/$abi"
            mkdir -p "$dst"
            # Named libpumpkin.so so Android extracts it as an executable
            # native library rather than blocking it under W^X in files dir.
            cp "$src" "$dst/libpumpkin.so"
            chmod +x "$dst/libpumpkin.so"
            size=$(du -sh "$dst/libpumpkin.so" | cut -f1)
            info "  -> $dst/libpumpkin.so ($size)"
        else
            warn "Binary not found at: $src"
            warn "Check manually: find target -name pumpkin -type f"
        fi
    done

    cd ..
}

# --- Verify output ---
verify_output() {
    info "Verifying output..."
    for pair in "${TARGETS[@]}"; do
        abi="${pair##*:}"
        f="$OUTPUT_DIR/$abi/libpumpkin.so"
        if [ -f "$f" ]; then
            file "$f" 2>/dev/null || true
            info "  OK  $f"
        else
            warn "  MISSING  $f"
        fi
    done
}

# --- Entry point ---
main() {
    echo ""
    echo "🎃  Pumpkin Android Cross-Compiler"
    echo "===================================="
    echo ""

    check_requirements
    install_targets
    install_cargo_ndk
    clone_pumpkin
    build_for_android
    verify_output

    echo ""
    info "Done! Binaries produced:"
    find "$OUTPUT_DIR" -type f -name "libpumpkin.so" 2>/dev/null
    echo ""
    info "Next step: open the project in Android Studio and build the APK."
}

main "$@"
