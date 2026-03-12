# Branch Protection Settings

Repository files alone cannot force review and approval on GitHub. To enforce
the project rule, configure branch protection for `main` with these settings:

## Required Settings

- Require a pull request before merging
- Require approvals: `1`
- Require review from Code Owners
- Dismiss stale pull request approvals when new commits are pushed
- Require status checks to pass before merging
- Required status check: `Minimal CI`
- Restrict direct pushes to `main`

## Reviewer

The repository-wide code owner is:

- `@gcassata74`

## Expected Outcome

With the settings above:

- nobody can merge into `main` without a pull request,
- every pull request requires review from `@gcassata74`,
- a single approval from `@gcassata74` is sufficient,
- merges are blocked until the minimal CI workflow passes.
