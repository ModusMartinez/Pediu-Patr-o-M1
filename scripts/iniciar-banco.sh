#!/bin/sh
set -eu

cd "$(dirname "$0")/.."

mongo_executavel="mongod"
if [ -x ".local/mongodb/bin/mongod" ]; then
    mongo_executavel="$PWD/.local/mongodb/bin/mongod"
fi

if ! command -v "$mongo_executavel" >/dev/null 2>&1; then
    echo "Instale o MongoDB Community antes de iniciar o banco." >&2
    exit 1
fi

mkdir -p .local/data/mongodb

# Ctrl+C encerra o banco normalmente. Os dados permanecem em .local/data/mongodb.
exec "$mongo_executavel" --dbpath "$PWD/.local/data/mongodb" --bind_ip 127.0.0.1 --port 27017
