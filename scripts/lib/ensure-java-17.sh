#!/usr/bin/env bash
# Shared JDK 17 discovery for Gradle wrapper scripts.

if [[ -z "${ENSURE_JAVA_17_LOADED:-}" ]]; then
  ENSURE_JAVA_17_LOADED=1

  ensure_java_17() {
    if [[ -n "${JAVA_HOME:-}" ]] && [[ -d "$JAVA_HOME" ]]; then
      return 0
    fi

    local candidate
    for candidate in \
      /usr/lib/jvm/java-17-temurin-jdk \
      /usr/lib/jvm/temurin-17-jdk \
      /usr/lib/jvm/java-17-openjdk; do
      if [[ -d "$candidate" ]]; then
        export JAVA_HOME="$candidate"
        return 0
      fi
    done

    echo "ensure-java-17: set JAVA_HOME to JDK 17 before running Gradle." >&2
    return 1
  }
fi
