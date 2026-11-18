#!/bin/bash
set -e
export JAVA_HOME=/home/hatch/android-tools/jdk
export PATH=$JAVA_HOME/bin:$PATH
BT=/home/hatch/android-tools/sdk/build-tools/34.0.0
PLAT=/home/hatch/android-tools/sdk/platforms/android-34/android.jar
cd /home/hatch/workspace/projects/catatan-uang-android
rm -rf build-manual && mkdir -p build-manual/gen build-manual/classes build-manual/dex
$BT/aapt2 compile --dir app/src/main/res -o build-manual/compiled.zip
$BT/aapt2 link -o build-manual/app-unsigned.apk -I $PLAT --manifest app/src/main/AndroidManifest.xml build-manual/compiled.zip --java build-manual/gen --min-sdk-version 24 --target-sdk-version 34 --version-code 1 --version-name 1.0
javac -encoding UTF-8 -source 17 -target 17 -cp $PLAT -d build-manual/classes $(find build-manual/gen app/src/main/java -name '*.java')
$BT/d8 --release --lib $PLAT --output build-manual/dex $(find build-manual/classes -name '*.class')
cd build-manual/dex && zip -q ../app-unsigned.apk classes.dex && cd ../..
$BT/zipalign -f 4 build-manual/app-unsigned.apk build-manual/app-aligned.apk
if [ ! -f build-manual/debug.keystore ]; then keytool -genkeypair -keystore build-manual/debug.keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null; fi
$BT/apksigner sign --ks build-manual/debug.keystore --ks-pass pass:android --key-pass pass:android --out build-manual/CatatanUang.apk build-manual/app-aligned.apk
$BT/apksigner verify build-manual/CatatanUang.apk && echo BUILD_MANUAL_SUKSES
