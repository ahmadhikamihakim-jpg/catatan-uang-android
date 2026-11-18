#!/bin/bash
# Build manual APK Catatan Uang versi Firebase (pipeline aapt2 + javac + d8 + apksigner).
set -e
PROJ="$(cd "$(dirname "$0")" && pwd)"
B="$PROJ/build-firebase"
SDK=~/android-tools/sdk
BT="$SDK/build-tools/34.0.0"
AJAR="$SDK/platforms/android-34/android.jar"
JDK=~/android-tools/jdk/bin
export JAVA_HOME=~/android-tools/jdk
export PATH="$JDK:$PATH"
KEYSTORE=~/workspace/projects/catatan-uang-android/build-manual/debug.keystore
OUT="$PROJ/CatatanUangFirebase.apk"

rm -rf "$B/classes" "$B/dex" "$B/gen" "$B/flat"; mkdir -p "$B/classes" "$B/dex" "$B/gen" "$B/flat"
python3 "$PROJ/prepare_libs.py" > "$B/prep.log"

# --- kumpulkan daftar pustaka
EXTRA_PKGS=""; RES_FLATS=""; CP=""
while IFS='|' read -r d pkg hasres name ch; do
  if [ "$d" = "JAR" ]; then CP="$CP:$pkg"; continue; fi
  EXTRA_PKGS="$EXTRA_PKGS --extra-packages $pkg"
  [ -f "$d/classes.jar" ] && CP="$CP:$d/classes.jar"
done < "$B/libs.txt"

# --- aapt2 compile: res aplikasi + res pustaka
"$BT/aapt2" compile --dir "$PROJ/app/src/main/res" -o "$B/flat/app.zip"
while IFS='|' read -r d pkg hasres name ch; do
  [ "$d" = "JAR" ] && continue
  if [ "$hasres" = "1" ]; then
    if "$BT/aapt2" compile --dir "$d/res" -o "$B/flat/$(basename $d).zip" 2>"$B/flat/$(basename $d).err"; then
      RES_FLATS="$RES_FLATS $B/flat/$(basename $d).zip"
    else
      echo "PERINGATAN: res gagal dikompilasi untuk $name (dilewati):"; cat "$B/flat/$(basename $d).err" | head -3
    fi
  fi
done < "$B/libs.txt"

# --- aapt2 link
"$BT/aapt2" link -I "$AJAR" --manifest "$B/AndroidManifest.xml" \
  --java "$B/gen" $EXTRA_PKGS --min-sdk-version 24 --target-sdk-version 34 \
  --version-code 12 --version-name 1.11 \
  -o "$B/base.apk" "$B/flat/app.zip" $RES_FLATS

# --- javac
find "$PROJ/app/src/main/java" "$B/gen" -name '*.java' > "$B/sources.txt"
"$JDK/javac" -encoding UTF-8 -source 8 -target 8 -nowarn -cp "$AJAR$CP" -d "$B/classes" @"$B/sources.txt"

# --- d8
LIBJARS=""
while IFS='|' read -r d pkg hasres name ch; do
  if [ "$d" = "JAR" ]; then LIBJARS="$LIBJARS $pkg";
  elif [ -f "$d/classes.jar" ]; then LIBJARS="$LIBJARS $d/classes.jar"; fi
done < "$B/libs.txt"
"$JDK/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --lib "$AJAR" --min-api 24 --output "$B/dex" \
  $(find "$B/classes" -name '*.class' | tr '\n' ' ') $LIBJARS

# --- periksa dex: tidak boleh ada supertype/interface yang hilang
python3 "$PROJ/verify_dex.py" "$B/dex"

# --- rakit APK
cp "$B/base.apk" "$B/unsigned.apk"
cd "$B/dex"
for f in classes*.dex; do zip -q "$B/unsigned.apk" "$f"; done
cd "$B"
"$BT/zipalign" -f 4 "$B/unsigned.apk" "$B/aligned.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT" "$B/aligned.apk"
echo "APK jadi: $OUT"
"$BT/apksigner" verify --print-certs "$OUT" | head -4
"$BT/aapt2" dump badging "$OUT" | head -8
