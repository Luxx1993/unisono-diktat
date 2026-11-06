#!/usr/bin/env bash
# Baut die Debug-APK und legt sie auf den Zweig `apk`, damit der feste Download-Link aus der README
# immer die neueste Version liefert:
#   https://github.com/Luxx1993/unisono-diktat/raw/apk/Dictate-Diktat-arm64.apk
#
# Aufruf im Hauptordner:  scripts/publish-apk.sh [--dry-run]
#   --dry-run  baut und prüft die arm64-APK (Pfad wird ausgegeben), schreibt aber nichts auf den Zweig `apk`.
# Voraussetzung: Android-SDK (ANDROID_HOME oder sdk.dir in local.properties), Schreibrecht auf origin.
#
# Die Debug-APK enthält native Bibliotheken für drei Prozessoren (rund 115 MB, über dem 100-MB-Limit von
# GitHub). Für das Galaxy S24 reicht arm64: das Skript entfernt die anderen, richtet die Datei neu aus und
# signiert sie wieder mit dem Debug-Schlüssel dieses Rechners (~/.android/debug.keystore, oder DEBUG_KEYSTORE).
#
# Wichtig: Dieselbe Signatur wie bei der bisher veröffentlichten APK, sonst lässt sich die neue Version nicht
# über die alte installieren. Das Skript vergleicht den Fingerabdruck mit der APK auf `apk` und bricht ab, wenn er
# abweicht (bewusster Schlüsselwechsel: ALLOW_NEW_KEY=1).
# Der Zweig `apk` behält immer nur die neueste APK (ein einziger Commit, per Force-Push ersetzt), damit das
# Repository nicht mit jeder Version um etwa 60 MB wächst. Ältere APKs sind dort bewusst nicht aufgehoben.
set -euo pipefail
cd "$(dirname "$0")/.."

dry_run=0
[ "${1:-}" = "--dry-run" ] && dry_run=1

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$sdk" ] || sdk=$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null | head -n1)
[ -n "$sdk" ] && [ -d "$sdk/build-tools" ] || { echo "Android-SDK nicht gefunden (ANDROID_HOME setzen)." >&2; exit 1; }
tools=$(ls -d "$sdk"/build-tools/* | sort -V | tail -n1)
keystore="${DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"
[ -f "$keystore" ] || { echo "Debug-Schlüssel fehlt: $keystore" >&2; exit 1; }

./gradlew :app:assembleDebug --no-configuration-cache

src=app/build/outputs/apk/debug/app-debug.apk
[ -f "$src" ] || src=$(ls app/build/outputs/apk/debug/*.apk | head -n1)
commit=$(git rev-parse --short HEAD)
branch=$(git rev-parse --abbrev-ref HEAD)

work=$(mktemp -d)
tmp=""
cleanup() {
    rm -rf "$work"
    [ -z "$tmp" ] || git worktree remove --force "$tmp" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# Nur arm64 behalten, neu ausrichten und mit dem Debug-Schlüssel signieren.
cp "$src" "$work/full.apk"
zip -q -d "$work/full.apk" 'lib/armeabi-v7a/*' 'lib/x86_64/*' >/dev/null || true
"$tools/zipalign" -f -p 4 "$work/full.apk" "$work/aligned.apk"
"$tools/apksigner" sign --ks "$keystore" --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey --out "$work/Dictate-Diktat-arm64.apk" "$work/aligned.apk"
apk="$work/Dictate-Diktat-arm64.apk"
"$tools/apksigner" verify "$apk"

fingerprint() { "$tools/apksigner" verify --print-certs "$1" 2>/dev/null | sed -n 's/.*certificate SHA-256 digest: //p' | head -n1; }
new_key=$(fingerprint "$apk")
sha=$(sha256sum "$apk" | cut -d' ' -f1)
size=$(du -h "$apk" | cut -f1)

if [ "$dry_run" = 1 ]; then
    keep="${TMPDIR:-/tmp}/Dictate-Diktat-arm64.apk"
    cp "$apk" "$keep"
    echo "Probelauf: $keep ($size, Schlüssel $new_key, SHA-256 $sha) – nichts veröffentlicht."
    exit 0
fi

tmp=$(mktemp -d)
git fetch -q origin apk
git worktree add -q --detach "$tmp" origin/apk

old_key=$(fingerprint "$tmp/Dictate-Diktat-arm64.apk" || true)
if [ -n "$old_key" ] && [ "$old_key" != "$new_key" ] && [ "${ALLOW_NEW_KEY:-0}" != 1 ]; then
    echo "Abbruch: Der Schlüssel dieses Rechners ($new_key) ist nicht der der veröffentlichten APK ($old_key)." >&2
    echo "Mit der neuen Datei ließe sich die installierte App nicht aktualisieren. Den alten Schlüssel nach" >&2
    echo "$keystore kopieren, oder bewusst ALLOW_NEW_KEY=1 setzen (dann vorher deinstallieren)." >&2
    exit 1
fi

cp "$apk" "$tmp/Dictate-Diktat-arm64.apk"
# version.json: what the in-app updater (AppUpdater) reads to decide whether there is something newer and to
# check the download. The version code comes from the APK itself, so it is exactly what Android compares.
badging=$("$tools/aapt2" dump badging "$apk" 2>/dev/null | sed -n 1p)  # sed reads to the end: head would trip pipefail (SIGPIPE)
version_code=$(printf '%s' "$badging" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p")
version_name=$(printf '%s' "$badging" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")
[ -n "$version_code" ] || { echo "Versionsnummer der APK nicht lesbar." >&2; exit 1; }
bytes=$(stat -c %s "$apk")
cat > "$tmp/version.json" <<JSON
{
  "versionCode": $version_code,
  "versionName": "$version_name",
  "commit": "$(git rev-parse HEAD)",
  "sha256": "$sha",
  "size": $bytes,
  "apk": "Dictate-Diktat-arm64.apk",
  "builtAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
JSON
sed -i -E "s/gebaut aus dem Zweig \`[^\`]+\` \(Commit [0-9a-f]+[^)]*\)/gebaut aus dem Zweig \`$branch\` (Commit $commit)/" "$tmp/README.md"
sed -i -E "s/^- SHA-256 der Datei: .*/- SHA-256 der Datei: \`$sha\`/" "$tmp/README.md"
sed -i -E "s/^- Signaturschlüssel: (.*)SHA-256 \`[0-9a-f]+\`/- Signaturschlüssel: \1SHA-256 \`$new_key\`/" "$tmp/README.md"

git -C "$tmp" checkout -q --orphan apk-neu
git -C "$tmp" add Dictate-Diktat-arm64.apk README.md version.json
git -C "$tmp" -c user.name="${GIT_AUTHOR_NAME:-$(git config user.name)}" -c user.email="${GIT_AUTHOR_EMAIL:-$(git config user.email)}" \
  commit -q -m "diktat als arm64-Debug-APK (Commit $commit)"
git -C "$tmp" push -q --force origin apk-neu:refs/heads/apk
# Der Hilfszweig ist nur lokal; wer ihn stehen lässt, lässt den nächsten Lauf scheitern ("a branch named apk-neu already exists").
git worktree remove --force "$tmp" 2>/dev/null || true
git branch -D apk-neu -q 2>/dev/null || true
echo "Fertig: https://github.com/Luxx1993/unisono-diktat/raw/apk/Dictate-Diktat-arm64.apk ($size, Version $version_name / $version_code, Schlüssel $new_key, SHA-256 $sha)"
