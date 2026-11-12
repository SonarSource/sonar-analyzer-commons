#!/usr/bin/env bash
# Copyright (C) SonarSource Sàrl
# For more information, see https://sonarsource.com/legal/
# mailto:info AT sonarsource DOT com

set -euo pipefail
case "${CI:-}" in
  true|1) ;;
  *) echo 'This package operation requires the authorized CI workflow and its credentials.' >&2; exit 2 ;;
esac

shopt -s nullglob
pkgs=("${NUPKG_DIR}"/*.nupkg)
if [[ ${#pkgs[@]} -eq 0 ]]; then
  echo "::error::No .nupkg found under ${NUPKG_DIR}"
  exit 1
fi
PKCS11_CFG="${SMTOOLS_PATH}/pkcs11properties.cfg"
if [[ ! -f "${PKCS11_CFG}" ]]; then
  echo "::error::Missing DigiCert PKCS#11 config at ${PKCS11_CFG}"
  ls -la "${SMTOOLS_PATH}" || true
  exit 1
fi

echo "=== DigiCert certificates ==="
smctl certificate ls || true
echo "=== DigiCert keypairs ==="
smctl keypair ls || true

# DigiCert ONE certificate alias (e.g. cert_*), not Vault SHA1 and not a guessed key_* .
CERT_ALIAS="$(smctl certificate ls | awk 'NR > 1 && toupper($4) == "ACTIVE" { print $2; exit }')"
KEYPAIR_ALIAS="$(smctl keypair ls | awk 'NR > 1 && toupper($0) ~ /ACTIVE/ { print $2; exit }')"
if [[ -z "${CERT_ALIAS}" && -z "${KEYPAIR_ALIAS}" ]]; then
  echo "::error::Could not resolve DigiCert certificate or keypair alias via smctl"
  exit 1
fi

sign_one() {
  local pkg="$1"
  local alias="$2"
  local mode="$3"
  echo "Signing ${pkg} with ${mode} alias=${alias}"
  case "${mode}" in
    DIGICERTONE)
      jsign \
        --storetype DIGICERTONE \
        --keystore "${SM_HOST}" \
        --storepass "${SM_API_KEY}|${SM_CLIENT_CERT_FILE}|${SM_CLIENT_CERT_PASSWORD}" \
        --alias "${alias}" \
        --tsaurl http://timestamp.digicert.com \
        --alg SHA-256 \
        --replace \
        "${pkg}"
      ;;
    PKCS11)
      jsign \
        --storetype PKCS11 \
        --keystore "${PKCS11_CFG}" \
        --storepass NONE \
        --alias "${alias}" \
        --tsaurl http://timestamp.digicert.com \
        --alg SHA-256 \
        --replace \
        "${pkg}"
      ;;
    *)
      echo "::error::Unknown sign mode ${mode}"
      return 1
      ;;
  esac
}

verify_one() {
  local pkg="$1"
  if jsign extract "${pkg}" > /dev/null 2>&1; then
    rm -f "${pkg}.sig"
    echo "Signature verified on ${pkg}"
    return 0
  fi
  rm -f "${pkg}.sig"
  return 1
}

for pkg in "${pkgs[@]}"; do
  signed=0
  # Prefer DigiCert ONE REST with certificate alias (same as sonarcloud-task-manager).
  if [[ -n "${CERT_ALIAS}" ]] && sign_one "${pkg}" "${CERT_ALIAS}" DIGICERTONE && verify_one "${pkg}"; then
    signed=1
  # PKCS11 with keypair alias (smctl-managed local module).
  elif [[ -n "${KEYPAIR_ALIAS}" ]] && sign_one "${pkg}" "${KEYPAIR_ALIAS}" PKCS11 && verify_one "${pkg}"; then
    signed=1
  # PKCS11 sometimes exposes the certificate alias instead of key_*.
  elif [[ -n "${CERT_ALIAS}" ]] && sign_one "${pkg}" "${CERT_ALIAS}" PKCS11 && verify_one "${pkg}"; then
    signed=1
  fi
  if [[ "${signed}" -ne 1 ]]; then
    echo "::error::Failed to DigiCert-sign ${pkg} (tried DIGICERTONE/PKCS11 with resolved aliases)"
    exit 1
  fi
done
