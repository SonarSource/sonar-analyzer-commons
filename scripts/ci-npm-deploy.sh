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
DEPLOY_REPO=sonarsource-npm-public-qa
PKG_NAME=analyzer-commons-configurations
# The assembly names the tarball with the 4-part Maven version, e.g. 2.28.0.1234.
SRC="${MODULE}/target/${MODULE}-${PROJECT_VERSION}-npm.tar.gz"
# npm needs semver: rewrite the trailing .BUILD to a -BUILD prerelease (2.28.0.1234 -> 2.28.0-1234).
# Must match the version baked into the tarball's package.json (${npm.package.version} in the module).
NPM_VERSION="$(printf '%s' "$PROJECT_VERSION" | sed -E 's/^([0-9]+\.[0-9]+\.[0-9]+)\.([0-9]+)$/\1-\2/')"
if [[ ! -f "$SRC" ]]; then
  echo "::error::Expected npm package not found at $SRC"
  ls -la "${MODULE}/target" || true
  exit 1
fi
# Separate build-info name so it does not collide with the Maven (github.repository) or _nuget build-info.
BUILD_NAME="${GITHUB_REPOSITORY#*/}_npm"
MODULE_GAV="org.sonarsource.analyzer-commons:@sonarsource/${PKG_NAME}:${NPM_VERSION}"
# Canonical scoped-npm layout path so Artifactory indexes it and the metadata's tarball URL is correct.
TARGET="${DEPLOY_REPO}/@sonarsource/${PKG_NAME}/-/${PKG_NAME}-${NPM_VERSION}.tgz"
jf config use repox
jf rt upload --flat \
  --build-name="$BUILD_NAME" --build-number="$BUILD_NUMBER" --module="$MODULE_GAV" \
  "$SRC" "$TARGET"
# The promote-npm job (ci-github-actions/promote@v1) reads these from the build-info to derive the
# target repo (…-qa -> …-builds) and the promoted version, so export them before build-collect-env
# captures the environment into buildInfo.env.
export ARTIFACTORY_DEPLOY_REPO="$DEPLOY_REPO"
export PROJECT_VERSION="$NPM_VERSION"
jf rt build-collect-env "$BUILD_NAME" "$BUILD_NUMBER"
jf rt build-publish "$BUILD_NAME" "$BUILD_NUMBER"
