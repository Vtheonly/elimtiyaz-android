#!/usr/bin/env bash
# =============================================================================
# consolidate-branches.sh — the 18-branch consolidation (the 133rd session)
# =============================================================================
# The owner mandate: "consolidate the 18 remaining branches into one unified
# branch." The verification below proves every remote branch's work is
# ALREADY CONTAINED in main (0 unmerged commits each — main IS the unified
# branch). What remains is deleting the stale remote refs so the repository
# carries exactly ONE branch.
#
# This script is CONTAINMENT-GUARDED: it refuses to delete any branch whose
# tip is not an ancestor of local main. It never force-deletes, never
# touches main, and never rewrites history.
#
# USAGE
#   ./scripts/consolidate-branches.sh            # proof only (safe, default)
#   ./scripts/consolidate-branches.sh --apply    # + delete the contained
#                                                #   remote branches (needs
#                                                #   push credentials)
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

APPLY=0
[ "${1:-}" = "--apply" ] && APPLY=1

git fetch --prune origin

PROTECTED="main"

echo "==> Containment proof: every remote branch vs local main"
unmerged_total=0
to_delete=()
while read -r ref; do
  case "$ref" in
    origin/*) ;;
    *) continue ;; # origin/HEAD shortens to "origin"; skip non-branch refs
  esac
  branch="${ref#origin/}"
  [ "$branch" = "HEAD" ] && continue
  [ "$branch" = "$PROTECTED" ] && continue
  if git merge-base --is-ancestor "origin/$branch" main 2>/dev/null; then
    n=$(git log --oneline "main..origin/$branch" 2>/dev/null | wc -l | tr -d ' ')
    printf '  [contained] %-42s (%s unmerged commits)\n' "$branch" "$n"
    to_delete+=("$branch")
  else
    n=$(git log --oneline "main..origin/$branch" 2>/dev/null | wc -l | tr -d ' ')
    printf '  [UNMERGED]  %-42s (%s commits NOT in main — REFUSING to delete)\n' "$branch" "$n"
    unmerged_total=$((unmerged_total + 1))
  fi
done < <(git for-each-ref --format='%(refname:short)' refs/remotes/origin)

echo
echo "    contained (safe to delete): ${#to_delete[@]}"
echo "    unmerged (protected):       $unmerged_total"

if [ "$APPLY" -ne 1 ]; then
  echo
  echo "Proof complete. To delete the ${#to_delete[@]} contained remote branches:"
  echo "  ./scripts/consolidate-branches.sh --apply"
  exit 0
fi

if [ "$unmerged_total" -gt 0 ]; then
  echo "REFUSING --apply: $unmerged_total branch(es) have unmerged work. Merge or"
  echo "explicitly review them first; this script never deletes unmerged work."
  exit 1
fi

echo "==> Deleting ${#to_delete[@]} contained remote branches (history is"
echo "    preserved forever in main's merge chain — nothing is lost)"
for b in "${to_delete[@]}"; do
  if git push origin --delete "$b"; then
    echo "  [deleted] $b"
  else
    echo "  [FAILED ] $b — check push credentials (the owner PAT), then re-run"
    exit 1
  fi
done

git fetch --prune origin
echo
echo "Done. Remaining remote branches:"
git for-each-ref --format='  %(refname:short)' refs/remotes/origin | grep -v '\->' || true
