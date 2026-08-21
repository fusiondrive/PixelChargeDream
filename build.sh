#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SDK_ROOT=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}
BUILD_TOOLS_VERSION=${BUILD_TOOLS_VERSION:-36.1.0}
PLATFORM_VERSION=${PLATFORM_VERSION:-android-37.0}
BUILD_TOOLS="$SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION"
ANDROID_JAR="$SDK_ROOT/platforms/$PLATFORM_VERSION/android.jar"
XPOSED_API=${XPOSED_API:-$HOME/.gradle/caches/modules-2/files-2.1/de.robv.android.xposed/api/82/35866b507b360d4789ff389ad7386b6e8bbf6cc4/api-82.jar}
BUILD_DIR="$SCRIPT_DIR/build"
CLASSES_DIR="$BUILD_DIR/classes"
DEX_DIR="$BUILD_DIR/dex"
UNSIGNED_APK="$BUILD_DIR/pixel-charge-dream-unsigned.apk"
OUTPUT_APK="$BUILD_DIR/pixel-charge-dream-debug.apk"
KEYSTORE="$BUILD_DIR/debug.keystore"
COMPILED_RES="$BUILD_DIR/compiled-res.zip"

for required_file in "$ANDROID_JAR" "$XPOSED_API"; do
    if [ ! -f "$required_file" ]; then
        printf 'Missing required file: %s\n' "$required_file" >&2
        exit 1
    fi
done

rm -rf "$CLASSES_DIR" "$DEX_DIR" "$UNSIGNED_APK" "$OUTPUT_APK" "$COMPILED_RES"
mkdir -p "$CLASSES_DIR" "$DEX_DIR"

find "$SCRIPT_DIR/src/main/java" -name '*.java' -print0 \
    | xargs -0 javac -source 8 -target 8 \
        -classpath "$ANDROID_JAR:$XPOSED_API" \
        -d "$CLASSES_DIR"

jar cf "$BUILD_DIR/classes.jar" -C "$CLASSES_DIR" .
"$BUILD_TOOLS/d8" \
    --lib "$ANDROID_JAR" \
    --min-api 31 \
    --output "$DEX_DIR" \
    "$BUILD_DIR/classes.jar"

"$BUILD_TOOLS/aapt2" compile \
    --dir "$SCRIPT_DIR/src/main/res" \
    -o "$COMPILED_RES"

"$BUILD_TOOLS/aapt2" link \
    -o "$UNSIGNED_APK" \
    -I "$ANDROID_JAR" \
    --manifest "$SCRIPT_DIR/AndroidManifest.xml" \
    --min-sdk-version 31 \
    --target-sdk-version 37 \
    "$COMPILED_RES"

(cd "$DEX_DIR" && zip -q -j "$UNSIGNED_APK" classes.dex)
(cd "$SCRIPT_DIR/src/main" && zip -q "$UNSIGNED_APK" assets/xposed_init)

if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -storepass android \
        -alias androiddebugkey \
        -keypass android \
        -dname "CN=Android Debug,O=Android,C=US" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -noprompt >/dev/null 2>&1
fi

"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias androiddebugkey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$OUTPUT_APK" \
    "$UNSIGNED_APK"

"$BUILD_TOOLS/apksigner" verify --verbose "$OUTPUT_APK"
printf 'Successfully built: %s\n' "$OUTPUT_APK"
