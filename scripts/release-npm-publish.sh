#!/usr/bin/env bash
# Copyright (C) SonarSource Sàrl
# For more information, see https://sonarsource.com/legal/
# mailto:info AT sonarsource DOT com

set -euo pipefail
case "${CI:-}" in
  true|1) ;;
  *) echo 'This package operation requires the authorized CI workflow and its credentials.' >&2; exit 2 ;;
esac

rm -f "${HOME}/.npmrc"
shopt -s nullglob
pkgs=(./npm-release/*.tgz)
if [[ ${#pkgs[@]} -eq 0 ]]; then
  echo "::error::No .tgz found under ./npm-release"
  exit 1
fi
# Publish the exact Repox archive so both registries serve identical bytes.
for pkg in "${pkgs[@]}"; do
  echo "Publishing ${pkg} via npm Trusted Publisher (OIDC)"
  # Build-style versions (e.g. 2.29.0-5138) are semver prereleases; npm requires --tag.
  npm publish "$pkg" --registry=https://registry.npmjs.org/ --provenance --access public --tag latest
done
