#!/usr/bin/env bash
# Copyright (C) SonarSource Sàrl
# For more information, see https://sonarsource.com/legal/
# mailto:info AT sonarsource DOT com

set -euo pipefail
case "${CI:-}" in
  true|1) ;;
  *) echo 'This package operation requires the authorized CI workflow and its credentials.' >&2; exit 2 ;;
esac

BUILD_NAME="${GITHUB_REPOSITORY#*/}_nuget"
# Pull requests are promoted to the dev feed; master/branch-* builds to the public feed.
if [[ "${GITHUB_EVENT_NAME}" == "pull_request" ]]; then
  TARGET_REPO=sonarsource-nuget-dev
  STATUS=it-passed-pr
else
  TARGET_REPO=sonarsource-nuget-public
  STATUS=it-passed
fi
jf config add repox \
  --artifactory-url="https://repox.jfrog.io/artifactory" \
  --access-token="$PROMOTER_ACCESS_TOKEN" \
  --interactive=false
jf config use repox
jf rt build-promote "$BUILD_NAME" "$BUILD_NUMBER" "$TARGET_REPO" \
  --status="$STATUS" \
  --source-repo="sonarsource-nuget-qa"
# build-promote returns success even when it moves 0 artifacts (e.g. a source-repo
# mismatch), which silently leaves nothing in the target feed. Assert the build's
# package actually landed so a no-op promotion fails the job instead of looking green.
FOUND=$(jf rt search --build="$BUILD_NAME/$BUILD_NUMBER" --count "$TARGET_REPO/*.nupkg")
if [[ "${FOUND:-0}" -lt 1 ]]; then
  echo "::error::NuGet promotion reported success but no .nupkg from ${BUILD_NAME}/${BUILD_NUMBER} is present in ${TARGET_REPO}"
  exit 1
fi
echo "Verified ${FOUND} NuGet package(s) from ${BUILD_NAME}/${BUILD_NUMBER} in ${TARGET_REPO}"
