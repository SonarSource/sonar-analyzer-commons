#!/usr/bin/env bash
# Copyright (C) SonarSource Sàrl
# For more information, see https://sonarsource.com/legal/
# mailto:info AT sonarsource DOT com

set -euo pipefail
case "${CI:-}" in
  true|1) ;;
  *) echo 'This package operation requires the authorized CI workflow and its credentials.' >&2; exit 2 ;;
esac

BUILD_NAME="${GITHUB_REPOSITORY#*/}_npm"
BUILD_NUMBER="${RELEASE_VERSION##*.}"
jf config add repox \
  --artifactory-url="https://repox.jfrog.io/artifactory" \
  --access-token="$PROMOTER_ACCESS_TOKEN" \
  --interactive=false
jf config use repox
rm -rf npm-release && mkdir npm-release
jf rt download --build="${BUILD_NAME}/${BUILD_NUMBER}" --flat "sonarsource-npm-public-releases/*.tgz" npm-release/
