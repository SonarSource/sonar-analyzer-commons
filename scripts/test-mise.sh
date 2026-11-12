#!/usr/bin/env bash
# Copyright (C) SonarSource Sàrl
# For more information, see https://sonarsource.com/legal/
# mailto:info AT sonarsource DOT com

set -euo pipefail
mise_bin=$1
auth=$("$mise_bin" config get 'doctor.checks."sonar:auth".run')
project=$("$mise_bin" config get 'doctor.checks."sonar:project".run')
analysis=$("$mise_bin" config get 'doctor.checks."sonar:analysis".run')
project_key=$("$mise_bin" config get vars.sonar_project)
project=${project//\{\{vars.sonar_project\}\}/$project_key}

test_directory=$(mktemp -d)
trap 'rm -rf "$test_directory"' EXIT
export DOCTOR_TEST_GH_CALLS="$test_directory/github-calls"
export GITHUB_TOKEN=explicit-fixture GH_TOKEN=other-fixture
export DOCTOR_TEST_EXPECTED_TOKEN=explicit-fixture
export DOCTOR_TEST_COVERAGE_FAIL=0 DOCTOR_TEST_SONAR_EXIT=0
export DOCTOR_TEST_RESPONSE='{"status":"connected","server":"https://next.sonarqube.com/sonarqube","org":"sonarsource"}'

# Exercise the declared commands with real jq and fake HTTP clients.
gh() {
  [[ ${GH_TOKEN:-stored-fixture} == "$DOCTOR_TEST_EXPECTED_TOKEN" ]] || return 1
  printf '.' >> "$DOCTOR_TEST_GH_CALLS"
  [[ $* != *coverage-update* || $DOCTOR_TEST_COVERAGE_FAIL == 0 ]]
}
sonar() {
  printf '%s\n' "$DOCTOR_TEST_RESPONSE"
  return "$DOCTOR_TEST_SONAR_EXIT"
}
export -f gh sonar

passed=0
expect() {
  local expected=$1 command=$2 result=0
  bash -e -o pipefail -c "$command" >/dev/null 2>&1 || result=$?
  if [[ $expected == pass && $result != 0 || $expected == fail && $result == 0 ]]; then
    printf 'Credential check case %d failed: expected %s.\n' "$((passed + 1))" "$expected" >&2
    exit 1
  fi
  passed=$((passed + 1))
}

expect pass "$auth"
for response in \
  '{"status":"not_authenticated"}' \
  '{"status":"connected","server":"https://sonarcloud.io","org":"sonarsource"}' \
  'not JSON'; do
  export DOCTOR_TEST_RESPONSE="$response"
  expect fail "$auth"
done
export DOCTOR_TEST_RESPONSE='{"status":"connected","server":"https://next.sonarqube.com/sonarqube","org":"sonarsource"}'
export DOCTOR_TEST_SONAR_EXIT=1
expect fail "$auth"

export DOCTOR_TEST_SONAR_EXIT=0
export DOCTOR_TEST_RESPONSE
DOCTOR_TEST_RESPONSE=$(jq -cn --arg key "$project_key" '{component:{key:$key}}')
expect pass "$project"
export DOCTOR_TEST_RESPONSE='{"component":{"key":"another-project"}}'
expect fail "$project"
export DOCTOR_TEST_RESPONSE='{}'
expect fail "$project"

export DOCTOR_TEST_RESPONSE='{"vortex":{"status":"enabled"}}'
expect pass "$analysis"
export DOCTOR_TEST_SONAR_EXIT=1
expect fail "$analysis"
export DOCTOR_TEST_SONAR_EXIT=0
for status in not_entitled over_consumption check_failed; do
  DOCTOR_TEST_RESPONSE=$(jq -cn --arg status "$status" '{healthy:true,vortex:{status:$status}}')
  expect fail "$analysis"
done
export DOCTOR_TEST_RESPONSE='{"healthy":true}'
expect fail "$analysis"

# Setup must warn and succeed when credentials are unavailable. Replace only the
# Mise executable in the declared task; no real login or network call is allowed.
setup=$("$mise_bin" config get tasks.setup.run)
setup=${setup//\{\{mise_bin | quote\}\}/setup_test_mise}
export DOCTOR_TEST_SETUP_CALLS="$test_directory/setup-calls"
export DOCTOR_TEST_LOGIN_CALLS="$test_directory/login-calls"
export DOCTOR_TEST_SETUP_EXIT=1
setup_test_mise() {
  [[ $* == 'doctor project' || $* == 'doctor project --json' ]] || return 2
  printf '.' >> "$DOCTOR_TEST_SETUP_CALLS"
  if [[ $* == *--json ]]; then
    printf '%s\n' '{"checks":[{"name":"rules:access","status":"fail"},{"name":"sonar:auth","status":"fail"}]}'
  fi
  return "$DOCTOR_TEST_SETUP_EXIT"
}
gh() { printf 'github\n' >> "$DOCTOR_TEST_LOGIN_CALLS"; }
sonar() { printf 'sonar\n' >> "$DOCTOR_TEST_LOGIN_CALLS"; }
export -f setup_test_mise gh sonar

expect_setup() {
  local warning=$1 terminal=${2:-no} checks=${3:-.} logins=${4:-} result=0 output=''
  : > "$DOCTOR_TEST_SETUP_CALLS"
  : > "$DOCTOR_TEST_LOGIN_CALLS"
  if [[ $terminal == yes ]]; then
    # Keep stdin and stderr attached so setup sees the terminal.
    bash -e -o pipefail -c "$setup" >/dev/null || result=$?
  else
    output=$(bash -e -o pipefail -c "$setup" </dev/null 2>&1) || result=$?
  fi
  if [[ $result != 0 || $(cat "$DOCTOR_TEST_LOGIN_CALLS") != "$logins" \
      || $(cat "$DOCTOR_TEST_SETUP_CALLS") != "$checks" \
      || $terminal == no && $warning == yes && $output != *'WARNING: Setup is incomplete'* \
      || $terminal == no && $warning == no && -n $output ]]; then
    printf 'Setup case %d failed: unexpected readiness or sign-in behavior.\n' "$((passed + 1))" >&2
    exit 1
  fi
  passed=$((passed + 1))
}

unset GITHUB_TOKEN GH_TOKEN SONARQUBE_CLI_TOKEN
export CI=false usage_interactive=false
expect_setup yes
export DOCTOR_TEST_SETUP_EXIT=0
expect_setup no
export DOCTOR_TEST_SETUP_EXIT=1 usage_interactive=true
expect_setup yes
export CI=true
expect_setup yes

if [[ -t 0 && -t 2 ]]; then
  export CI=false usage_interactive=false
  expect_setup yes yes
  export usage_interactive=true
  expect_setup yes yes '...' sonar
  export CI=true
  expect_setup yes yes
  export CI=false GITHUB_TOKEN=explicit-fixture SONARQUBE_CLI_TOKEN=explicit-fixture
  expect_setup yes yes '...'
else
  printf 'Terminal setup cases require mise run --raw test:tooling in a terminal.\n'
fi

printf '%d credential and setup check cases passed.\n' "$passed"

# Run the actual task entry points. No Maven build or HTTP client is executed.
export MISE_TEST_CALLS="$test_directory/maven-calls"
export MISE_TEST_ROOT="$PWD"
export MISE_TEST_MAVEN_EXIT=0
mvn() {
  printf '%s' "$PWD" >> "$MISE_TEST_CALLS"
  printf ' <%s>' "$@" >> "$MISE_TEST_CALLS"
  printf '\n' >> "$MISE_TEST_CALLS"
  return "$MISE_TEST_MAVEN_EXIT"
}
export -f mvn
run_task() {
  : > "$MISE_TEST_CALLS"
  "$mise_bin" run "$@" >/dev/null 2>&1
}
run_task test
[[ $(wc -l < "$MISE_TEST_CALLS") == 1 ]]
[[ $(cat "$MISE_TEST_CALLS") == *'<test> <-DskipTests=false>'* ]]
module=$(python -c 'import xml.etree.ElementTree as E; print(E.parse("pom.xml").find("{*}modules/{*}module").text)')
run_task test "$module"
[[ $(cat "$MISE_TEST_CALLS") == *"<test> <-pl> <$module> <-am> <-DskipTests=false>"* ]]
run_task test "$module" 'SelectedTest#method'
[[ $(wc -l < "$MISE_TEST_CALLS") == 2 ]]
[[ $(head -1 "$MISE_TEST_CALLS") == *"<install> <-pl> <$module> <-am> <-DskipTests>"* ]]
[[ $(tail -1 "$MISE_TEST_CALLS") == *"<test> <-pl> <$module> <-Dtest=SelectedTest#method> <-DfailIfNoTests=true> <-DskipTests=false>"* ]]
[[ $(tail -1 "$MISE_TEST_CALLS") != *'<-am>'* ]]
# Module-directory invocation must still run against the root reactor.
(cd "$module"; run_task test "$module")
[[ $(cat "$MISE_TEST_CALLS") == "$MISE_TEST_ROOT "* ]]
export MISE_TEST_MAVEN_EXIT=17
if run_task test "$module" 'SelectedTest#method'; then
  echo 'A failed dependency install must fail the task.' >&2; exit 1
fi
[[ $(wc -l < "$MISE_TEST_CALLS") == 1 ]]
export MISE_TEST_MAVEN_EXIT=0
# The shared CI action and local verification use the same profile and test flag.
export GITHUB_OUTPUT="$test_directory/github-output"
"$mise_bin" run ci:configure >/dev/null 2>&1
[[ $(cat "$GITHUB_OUTPUT") == 'maven-args=-Pcoverage -DskipTests=false' ]]
# Bash tasks keep arguments with spaces intact.
run_task lint --fix
[[ $(cat "$MISE_TEST_CALLS") == *'spotless-maven-plugin:apply>'* ]]
run_task lint
[[ $(cat "$MISE_TEST_CALLS") == *'spotless-maven-plugin:check>'* ]]
printf 'Mise task selection, failure propagation, root directory, and CI options passed.\n'

python -m unittest discover -s scripts -p 'test_packages.py'
