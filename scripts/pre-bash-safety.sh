#!/usr/bin/env bash
# Pre-bash safety hook — blocks destructive commands
# Reads the command from CLAUDE_TOOL_INPUT_COMMAND env var (set by Claude Code harness)
# Exit code 2 = block the command; exit 0 = allow

set -euo pipefail

COMMAND="${CLAUDE_TOOL_INPUT_COMMAND:-}"

# Patterns to block
DANGEROUS_PATTERNS=(
    "rm -rf"
    "git push --force"
    "git push -f "
    "DROP TABLE"
    "DROP DATABASE"
    "TRUNCATE TABLE"
    "DELETE FROM .* WHERE 1=1"
    "git reset --hard"
)

for pattern in "${DANGEROUS_PATTERNS[@]}"; do
    if echo "$COMMAND" | grep -qiE "$pattern"; then
        echo "BLOCKED by pre-bash-safety.sh: command matches dangerous pattern '$pattern'" >&2
        echo "To bypass: add --confirm flag or run the command manually outside Claude Code" >&2
        exit 2
    fi
done

exit 0
