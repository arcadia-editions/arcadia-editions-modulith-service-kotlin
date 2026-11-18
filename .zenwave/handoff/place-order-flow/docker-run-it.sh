#!/usr/bin/env bash
# Runs one Testcontainers command under a lock, for clones that have T-37 (test compose on ports 15432/29092/22181/18081,
# so no clash with modulith-service-java nor with stale Rancher relays on 5432).
# Usage (from the clone): bash <this file> mvn -q verify -Dit.test=... -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false
# absolute, next to this script: one lock for every clone (they all share the test compose database on 15432)
LOCK="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/docker-it.lock"
# nested call (an outer run of this script already holds the lock): run directly, never wait on our own lock
if [ -n "${DOCKER_IT_LOCK_HELD:-}" ]; then export JAVA_HOME=${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}; exec "$@"; fi
export DOCKER_IT_LOCK_HELD=1
until mkdir "$LOCK" 2>/dev/null; do
  # break a lock left by a dead holder (no run takes 40 minutes)
  [ -n "$(find "$LOCK" -maxdepth 0 -mmin +40 2>/dev/null)" ] && rmdir "$LOCK" 2>/dev/null
  sleep 15
done
trap 'rmdir "$LOCK" 2>/dev/null' EXIT
# no port wait: the shared test compose keeps 15432 open on purpose; the lock is what serializes runs
export JAVA_HOME=${JAVA_HOME:-$HOME/.sdkman/candidates/java/current}
"$@"
