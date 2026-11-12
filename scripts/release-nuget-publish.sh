#!/usr/bin/env bash
# Copyright (C) SonarSource Sàrl
# For more information, see https://sonarsource.com/legal/
# mailto:info AT sonarsource DOT com

set -euo pipefail
case "${CI:-}" in
  true|1) ;;
  *) echo 'This package operation requires the authorized CI workflow and its credentials.' >&2; exit 2 ;;
esac

dotnet nuget push "nuget-release/*.nupkg" \
  --api-key "$NUGET_API_KEY" \
  --source https://api.nuget.org/v3/index.json \
  --skip-duplicate
