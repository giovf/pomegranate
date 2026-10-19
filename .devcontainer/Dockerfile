FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

# ============================================================================
# GLOBAL LAYER — stack-agnostic. Every project gets this.
# Project-specific packages go in the marked section at the bottom, added by
# Claude during /setup (followed by a one-time container rebuild).
# ============================================================================

# ---------- System packages ----------
# - build-essential, python3: needed by many native npm modules and tooling
# - ripgrep, fd-find, jq, fzf: CLI tools Claude Code uses well
RUN apt-get update \
 && export DEBIAN_FRONTEND=noninteractive \
 && apt-get install -y --no-install-recommends \
      curl \
      git \
      build-essential \
      python3 \
      python3-pip \
      python3-venv \
      pipx \
      unzip \
      zip \
      ca-certificates \
      fzf \
      jq \
      ripgrep \
      fd-find \
 && apt-get clean -y && rm -rf /var/lib/apt/lists/*

# ---------- Node.js 22 LTS ----------
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
 && apt-get install -y nodejs \
 && apt-get clean -y && rm -rf /var/lib/apt/lists/*

# ---------- Persistent shell history (mounted as a volume in devcontainer.json) ----------
RUN mkdir -p /commandhistory \
 && touch /commandhistory/.zsh_history \
 && chown -R vscode:vscode /commandhistory \
 && echo 'export HISTFILE=/commandhistory/.zsh_history' >> /home/vscode/.zshrc

# ---------- npm global prefix in user dir (no sudo for global installs) ----------
USER vscode
ENV NPM_CONFIG_PREFIX=/home/vscode/.npm-global
ENV PATH=/home/vscode/.npm-global/bin:$PATH
RUN mkdir -p /home/vscode/.npm-global \
 && echo 'export NPM_CONFIG_PREFIX=/home/vscode/.npm-global' >> /home/vscode/.zshrc \
 && echo 'export PATH=/home/vscode/.npm-global/bin:$PATH'    >> /home/vscode/.zshrc \
 && echo 'export NPM_CONFIG_PREFIX=/home/vscode/.npm-global' >> /home/vscode/.bashrc \
 && echo 'export PATH=/home/vscode/.npm-global/bin:$PATH'    >> /home/vscode/.bashrc

# ---------- Pre-installed JS toolchain (global for all projects) ----------
# typescript      — TS compiler
# pnpm, yarn      — alternate package managers (npm is the default)
# task-master-ai  — task management CLI + MCP server
RUN npm install -g \
      typescript \
      pnpm \
      yarn \
      task-master-ai

# ============================================================================
# PROJECT-SPECIFIC LAYER — edited by Claude during /setup.
#
# Examples of what belongs here:
#   Expo/React Native:  apt: watchman, openjdk-17-jdk   npm -g: eas-cli
#   Native/embedded:    apt: cmake, ninja-build
#   Data/ML:            apt/pip: handled via project venv instead where possible
#
# After editing, the user must rebuild the container
# (Command Palette -> "Dev Containers: Rebuild Container").
# ============================================================================
# <<PROJECT-DEPS-START>>
# Pomegranate: native Android (Kotlin) app. Needs JDK 17 + the Android SDK.
#
# This image is arm64 (Apple Silicon host). Google only ships Android
# build-tools (aapt2 etc.) for Linux x86_64; they run here through Docker
# Desktop's Rosetta emulation, which needs the amd64 C runtime installed
# alongside the arm64 one (Ubuntu multiarch). Keep "Use Rosetta for
# x86_64/amd64 emulation" enabled in Docker Desktop settings.
# Fallback if emulation is ever unavailable: apt install the arm64 `aapt`
# package and set android.aapt2FromMavenOverride=/usr/bin/aapt2 in
# gradle.properties.
USER root

# ---- Ubuntu multiarch: arm64 from ports.ubuntu.com, amd64 from archive.ubuntu.com
RUN sed -i 's|^Components: \(.*\)$|Components: \1\nArchitectures: arm64|' \
      /etc/apt/sources.list.d/ubuntu.sources \
 && printf '%s\n' \
      'Types: deb' \
      'URIs: http://archive.ubuntu.com/ubuntu/' \
      'Suites: noble noble-updates noble-security' \
      'Components: main universe' \
      'Architectures: amd64' \
      'Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg' \
      > /etc/apt/sources.list.d/ubuntu-amd64.sources \
 && dpkg --add-architecture amd64 \
 && apt-get update \
 && export DEBIAN_FRONTEND=noninteractive \
 && apt-get install -y --no-install-recommends \
      openjdk-17-jdk-headless \
      libc6:amd64 libstdc++6:amd64 zlib1g:amd64 \
 && apt-get clean -y && rm -rf /var/lib/apt/lists/*

# ---- Android SDK (Google command-line tools, pinned + checksum-verified)
# Version/checksum from https://developer.android.com/studio#command-line-tools-only
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
ARG CLT_VERSION=15859902
ARG CLT_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
 && curl -fsSL -o /tmp/clt.zip \
      https://dl.google.com/android/repository/commandlinetools-linux-${CLT_VERSION}_latest.zip \
 && echo "${CLT_SHA256}  /tmp/clt.zip" | sha256sum -c - \
 && unzip -q /tmp/clt.zip -d ${ANDROID_HOME}/cmdline-tools \
 && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
 && rm /tmp/clt.zip \
 && chown -R vscode:vscode ${ANDROID_HOME}
ENV PATH=${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:$PATH

USER vscode
RUN yes | sdkmanager --licenses > /dev/null \
 && sdkmanager --install \
      "platform-tools" \
      "platforms;android-35" \
      "build-tools;35.0.0" \
 && for rc in /home/vscode/.zshrc /home/vscode/.bashrc; do \
      echo "export ANDROID_HOME=${ANDROID_HOME}" >> $rc; \
      echo "export ANDROID_SDK_ROOT=${ANDROID_HOME}" >> $rc; \
      echo "export JAVA_HOME=${JAVA_HOME}" >> $rc; \
      echo 'export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH' >> $rc; \
    done
# <<PROJECT-DEPS-END>>

WORKDIR /workspaces
