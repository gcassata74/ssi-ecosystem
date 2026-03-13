<!--
  SSI Ecosystem
  Copyright (c) 2026-present Izylife Solutions s.r.l.
  Author: Giuseppe Cassata

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Affero General Public License as published
  by the Free Software Foundation, either version 3 of the License,
  or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Affero General Public License for more details.

  You should have received a copy of the GNU Affero General Public License
  along with this program. If not, see <https://www.gnu.org/licenses/>.
-->

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
