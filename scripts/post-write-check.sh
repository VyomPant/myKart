#!/usr/bin/env bash
# Post-write check hook — lints Kotlin, Java, and SQL files after Claude writes them
# Reads the file path from CLAUDE_TOOL_INPUT_FILE_PATH env var

set -euo pipefail

FILE_PATH="${CLAUDE_TOOL_INPUT_FILE_PATH:-}"

if [[ -z "$FILE_PATH" ]]; then
    exit 0
fi

EXTENSION="${FILE_PATH##*.}"

case "$EXTENSION" in
    kt)
        if command -v ktlint &>/dev/null; then
            echo "Running ktlint on $FILE_PATH"
            ktlint --lint "$FILE_PATH" || {
                echo "ktlint found issues in $FILE_PATH" >&2
                exit 1
            }
        else
            echo "ktlint not found — skipping Kotlin lint (install: brew install ktlint)"
        fi
        ;;
    java)
        # Lightweight check: verify the file has a matching class name and package declaration
        FILENAME=$(basename "$FILE_PATH" .java)
        if ! grep -q "class $FILENAME\|interface $FILENAME\|record $FILENAME\|enum $FILENAME" "$FILE_PATH"; then
            echo "Warning: $FILE_PATH may have a class/file name mismatch" >&2
        fi
        ;;
    sql)
        # Validate Flyway naming convention: V{n}__{description}.sql
        BASENAME=$(basename "$FILE_PATH")
        if [[ "$BASENAME" =~ ^V[0-9]+__[a-z0-9_]+\.sql$ ]]; then
            echo "Flyway migration naming OK: $BASENAME"
        elif [[ "$BASENAME" =~ ^[VUR] ]]; then
            echo "Warning: $BASENAME does not match Flyway convention V{n}__{lowercase_description}.sql" >&2
        fi
        ;;
esac

exit 0
