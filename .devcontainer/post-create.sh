#!/usr/bin/env bash
set -euo pipefail

echo "→ Running post-create setup..."

# ----------------------------------------------------------------------
# Fix volume ownership.
#
# Docker creates named volumes as root-owned by default. The devcontainer
# 'claude-code' feature also installs npm globals as root during image
# build. Both bite the vscode user later — Claude can't save credentials,
# `npm install -g` fails with EACCES, etc. Fix them all upfront.
# ----------------------------------------------------------------------
echo "→ Fixing ownership of persistent dirs..."
sudo chown -R vscode:vscode \
  "$HOME/.claude" \
  "$HOME/.npm-global" \
  "$HOME/.npm" \
  /commandhistory \
  2>/dev/null || true

# node_modules is large and lives on a volume; only chown if root currently owns it
if [ -d "node_modules" ] && [ "$(stat -c '%U' node_modules)" = "root" ]; then
  echo "→ Fixing node_modules ownership (one-time, can take a minute)..."
  sudo chown vscode:vscode node_modules
fi

# ----------------------------------------------------------------------
# Claude Code stores auth in TWO places:
#   ~/.claude/             (sessions, history, custom commands, .credentials.json)
#   ~/.claude.json         (a sibling file in $HOME — global config + flags)
#
# We mount a named Docker volume at ~/.claude so it survives rebuilds.
# We can't mount a single file on top of $HOME, so we keep the canonical
# .claude.json *inside* the volume and symlink it back to $HOME.
# ----------------------------------------------------------------------
mkdir -p "$HOME/.claude"

# If a real .claude.json exists in $HOME (not a symlink), move it into the
# volume so it persists. Handles the case where Claude wrote the file
# before the symlink was set up.
if [ -f "$HOME/.claude.json" ] && [ ! -L "$HOME/.claude.json" ]; then
  echo "→ Moving existing .claude.json into the persistent volume..."
  mv "$HOME/.claude.json" "$HOME/.claude/.claude.json"
fi

# Seed a minimal config the first time so Claude Code skips onboarding.
if [ ! -f "$HOME/.claude/.claude.json" ]; then
  echo '{"hasCompletedOnboarding": true}' > "$HOME/.claude/.claude.json"
fi

# Ensure $HOME/.claude.json is a symlink into the volume.
ln -sf "$HOME/.claude/.claude.json" "$HOME/.claude.json"

# ----------------------------------------------------------------------
# Disable Claude Code's auto-updater. Predictable versions in a container
# are better than surprise updates mid-session. Update manually with:
#   npm install -g @anthropic-ai/claude-code@latest
# ----------------------------------------------------------------------
for rc in ~/.zshrc ~/.bashrc; do
  if [ -f "$rc" ] && ! grep -q DISABLE_AUTOUPDATER "$rc"; then
    echo 'export DISABLE_AUTOUPDATER=1' >> "$rc"
  fi
done

# ----------------------------------------------------------------------
# If a package.json already exists, install deps (re-running after rebuilds
# is cheap because node_modules lives on a named volume).
# ----------------------------------------------------------------------
if [ -f package.json ]; then
  echo "→ package.json detected — running npm install..."
  npm install
fi

cat <<'EOF'

✓ Pomegranate container ready.
  Build:  ./gradlew assembleDebug      Tests: ./gradlew testDebugUnitTest
  Claude: claude  (then `task-master next` for the current task)

EOF
