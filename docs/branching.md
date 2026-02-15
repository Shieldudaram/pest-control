# Branching Strategy

## Branch roles
- `dev`: primary integration branch for feature and fix work.
- `main`: release branch.

## Pull request targets
- Open normal feature/fix PRs against `dev`.
- PRs targeting `main` are blocked by CI unless they carry the `release-sync` label.

## Release sync (`dev` -> `main`)
Use this checklist for manual release synchronization:

1. Verify `dev` CI is green.
2. Update local refs (`git fetch origin --prune`).
3. Update `main` locally and fast-forward it to `dev`:
   - `git switch main`
   - `git pull --ff-only origin main`
   - `git merge --ff-only origin/dev`
4. Push updated `main`:
   - `git push origin main`
5. Optionally create a release tag from `main`.

## Exceptional PRs to `main`
- Allowed only for approved release operations.
- Must include the `release-sync` label to pass policy checks.
