#!/bin/sh
set -e
SDK=${SDK:-$HOME/sdk/android-33.jar}
NAME=${1:-fama-0.3.0}
OUT=$PWD/out
rm -rf build "$OUT"; mkdir -p build/classes build/gen build/assets/modules "$OUT"

# The package carries the lexicon, the ways, and the language modules beside the code.
cp assets/*.txt build/assets/
cp modules/ru.txt build/assets/modules/ru.txt

aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -I "$SDK"
javac -source 8 -target 8 -bootclasspath "$SDK" -classpath "$SDK" \
  -d build/classes -encoding UTF-8 -nowarn \
  $(find src build/gen -name '*.java')
dalvik-exchange --dex --min-sdk-version=26 --output=build/classes.dex build/classes
aapt package -f -M AndroidManifest.xml -S res -A build/assets -I "$SDK" -F build/base.apk
cd build && aapt add -f base.apk classes.dex >/dev/null && cd ..
zipalign -f 4 build/base.apk build/aligned.apk
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KSPASS" \
  --out "$OUT/$NAME.apk" build/aligned.apk
apksigner verify --print-certs "$OUT/$NAME.apk" | grep -i 'SHA-256 digest'
ls -la "$OUT/$NAME.apk"
