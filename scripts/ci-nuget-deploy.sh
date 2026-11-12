#!/usr/bin/env bash
# Copyright (C) SonarSource Sàrl
# For more information, see https://sonarsource.com/legal/
# mailto:info AT sonarsource DOT com

set -euo pipefail
case "${CI:-}" in
  true|1) ;;
  *) echo 'This package operation requires the authorized CI workflow and its credentials.' >&2; exit 2 ;;
esac

# Separate build-info name so it does not collide with the Maven build-info (github.repository name).
BUILD_NAME="${GITHUB_REPOSITORY#*/}_nuget"
MODULE_GAV="org.sonarsource.analyzer-commons:${NUPKG_ID}:${NUPKG_VERSION}"
jf config add repox \
  --artifactory-url="https://repox.jfrog.io/artifactory" \
  --access-token="$ARTIFACTORY_ACCESS_TOKEN" \
  --interactive=false
jf config use repox
jf rt upload --flat \
  --build-name="$BUILD_NAME" --build-number="$BUILD_NUMBER" --module="$MODULE_GAV" \
  "${NUPKG_DIR}/*.nupkg" "sonarsource-nuget-qa/"
jf rt build-collect-env "$BUILD_NAME" "$BUILD_NUMBER"
jf rt build-publish "$BUILD_NAME" "$BUILD_NUMBER"
