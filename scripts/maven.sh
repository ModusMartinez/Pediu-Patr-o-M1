#!/bin/sh
set -eu

cd "$(dirname "$0")/.."

# Usa o Java instalado nesta pasta, quando disponível.
if [ -d ".local/jdk/Contents/Home" ]; then
    export JAVA_HOME="$PWD/.local/jdk/Contents/Home"
fi

# Mantém os downloads do Maven dentro da pasta ignorada pelo Git.
export MAVEN_USER_HOME="$PWD/.local/maven"

exec ./mvnw "-Dmaven.repo.local=$PWD/.local/maven/repository" "$@"
