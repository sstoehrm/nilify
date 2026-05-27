#!/usr/bin/env bash
set -euo pipefail

REPO="sstoehrm/nilify"
INSTALL_DIR="${NILIFY_INSTALL_DIR:-$HOME/.local/bin}"
BINARY_NAME="nilify"

main() {
  check_deps
  install_binary
  verify_path
  echo ""
  echo "nilify installed successfully!"
  echo "Run 'nilify help' to get started."
}

check_deps() {
  if ! command -v bb &> /dev/null; then
    echo "Error: babashka (bb) is required but not installed."
    echo "Install it from: https://github.com/babashka/babashka"
    exit 1
  fi

  if ! command -v curl &> /dev/null; then
    echo "Error: curl is required but not installed."
    exit 1
  fi
}

install_binary() {
  mkdir -p "$INSTALL_DIR"

  echo "Downloading nilify..."
  local url="https://raw.githubusercontent.com/${REPO}/main/nilify"
  local tmp=$(mktemp)

  if curl -fsSL "$url" -o "$tmp"; then
    mv "$tmp" "${INSTALL_DIR}/${BINARY_NAME}"
    chmod +x "${INSTALL_DIR}/${BINARY_NAME}"
    echo "Installed to ${INSTALL_DIR}/${BINARY_NAME}"
  else
    rm -f "$tmp"
    echo "Error: failed to download nilify from ${url}"
    exit 1
  fi
}

verify_path() {
  if [[ ":$PATH:" != *":${INSTALL_DIR}:"* ]]; then
    echo ""
    echo "Warning: ${INSTALL_DIR} is not in your PATH."
    echo "Add this to your shell profile:"
    echo "  export PATH=\"${INSTALL_DIR}:\$PATH\""
  fi
}

main
