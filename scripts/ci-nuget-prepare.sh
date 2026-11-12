#!/usr/bin/env bash
# Copyright (C) SonarSource Sàrl
# For more information, see https://sonarsource.com/legal/
# mailto:info AT sonarsource DOT com

set -euo pipefail
case "${CI:-}" in
  true|1) ;;
  *) echo 'This package operation requires the authorized CI workflow and its credentials.' >&2; exit 2 ;;
esac

MODULE=sonar-analyzer-commons-configurations
# package.version is project.version with -SNAPSHOT stripped (in CI there is no -SNAPSHOT suffix).
PKG_VERSION="${PROJECT_VERSION%-SNAPSHOT}"
# Must equal the nuspec <id>; the feed derives the package identity from the "<id>.<version>.nupkg" name.
PKG_ID=SonarAnalyzer.CommonsConfigurations
SRC="${MODULE}/target/${MODULE}-${PROJECT_VERSION}-nupkg.zip"
OUT_DIR=nuget-staging
if [[ ! -f "$SRC" ]]; then
  echo "::error::Expected NuGet package not found at $SRC"
  ls -la "${MODULE}/target" || true
  exit 1
fi
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
cp "$SRC" "${OUT_DIR}/${PKG_ID}.${PKG_VERSION}.nupkg"
{
  echo "dir=${OUT_DIR}"
  echo "id=${PKG_ID}"
  echo "version=${PKG_VERSION}"
} >> "$GITHUB_OUTPUT"
