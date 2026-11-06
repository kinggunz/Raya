#!/data/data/com.termux/files/usr/bin/bash
# Voice Gunz - upload ke GitHub, build lewat GitHub Actions, lalu unduh APK.
# Jalankan di Termux dari dalam folder proyek:   bash termux-push.sh

set -e

echo "== Menyiapkan alat (git, gh) =="
pkg update -y
pkg install -y git gh

echo
read -r -p "Username GitHub        : " GH_USER
read -r -p "Nama repo (voice-gunz) : " REPO
REPO=${REPO:-voice-gunz}
read -r -s -p "Token GitHub (scope: repo + workflow): " GH_TOKEN
echo

echo "$GH_TOKEN" | gh auth login --with-token
gh auth setup-git

git config --global user.name "$GH_USER"
git config --global user.email "${GH_USER}@users.noreply.github.com"

if [ ! -d .git ]; then
  git init -b main
fi
git add -A
git commit -m "Voice Gunz" || true
git branch -M main

if gh repo view "$GH_USER/$REPO" >/dev/null 2>&1; then
  git remote remove origin 2>/dev/null || true
  git remote add origin "https://github.com/$GH_USER/$REPO.git"
  git push -u origin main --force
else
  gh repo create "$GH_USER/$REPO" --private --source=. --remote=origin --push
fi

echo
echo "== Menunggu build GitHub Actions =="
sleep 12
RUN_ID=$(gh run list --repo "$GH_USER/$REPO" --limit 1 --json databaseId -q '.[0].databaseId')
gh run watch "$RUN_ID" --repo "$GH_USER/$REPO" --exit-status

echo
echo "== Mengunduh APK =="
if [ ! -d "$HOME/storage/downloads" ]; then
  termux-setup-storage
  sleep 5
fi
OUT="$HOME/storage/downloads/VoiceGunz"
mkdir -p "$OUT"
gh run download "$RUN_ID" --repo "$GH_USER/$REPO" -n VoiceGunz-debug-apk -D "$OUT"

echo
echo "Selesai! APK ada di: Download/VoiceGunz/app-debug.apk"
echo "Buka lewat file manager lalu install."
