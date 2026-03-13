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

# Contributing

## Scope

This repository contains multiple modules with different responsibilities. Keep
changes small, reviewable, and limited to the module you are actually touching.

## Basic Workflow

1. Create a branch for your work.
2. Make the smallest coherent change that solves the problem.
3. Add or update tests and documentation when behavior changes.
4. Run the relevant checks for the modules you touched.
5. Commit with a clear message.
6. Push the branch to the remote repository.
7. Open a pull request.

## Mandatory Push Policy

If you modify, fix, refactor, or improve code in this repository, the change is
not considered a valid contribution until it has been committed and pushed to a
remote branch.

Required:

- Every code contribution must be committed.
- Every committed contribution must be pushed to the remote repository.
- Every pushed contribution must be proposed through a pull request unless a
  maintainer explicitly asks for a different flow.

Not accepted as completed contributions:

- local-only changes left unpushed,
- screenshots of code instead of commits,
- patch descriptions without the corresponding pushed branch,
- undocumented behavior changes.

## Important Limitation

Git repositories cannot reliably force every contributor to run `git push`
using only files stored inside the repository. Real enforcement requires remote
repository controls such as:

- protected branches,
- mandatory pull requests,
- required status checks,
- restricted direct pushes.

This file defines the project rule. Maintainers should also enable those remote
controls on the hosting platform to enforce it operationally.

## Branch And Review Rules

- Do not push directly to the main protected branch.
- Use pull requests for all normal code changes.
- Request review from `@gcassata74` on every pull request.
- A change must not be merged without 1 approval from `@gcassata74`.
- Keep pull requests focused on one topic.
- Describe what changed, why it changed, and how it was verified.
- Call out migrations, config changes, and breaking behavior explicitly.
- The repository owner must enable GitHub branch protection on `main` for this
  to be enforced. See `.github/BRANCH_PROTECTION.md`.

## Quality Expectations

- Preserve existing architecture unless there is a documented reason to change
  it.
- Prefer small, reversible changes over large rewrites.
- Keep public APIs and protocol behavior stable unless the change is deliberate.
- Update `README.md` or module documentation when workflows or architecture
  change.
- Do not commit secrets, private keys, credentials, or environment-specific
  data.

## Testing

Run the checks that match the modules you touched.

The repository CI intentionally stays minimal. The required baseline is:

- `ssi-client-lib`: install, type-check, build
- `ssi-client-application/backend`: `mvn test`

Those checks are the ones intended to remain required and green for routine
changes unless the project owner deliberately raises the bar.

Examples:

- `ssi-issuer-verifier`: `mvn test`
- `ssi-client-application/backend`: `mvn -f backend/pom.xml test`
- `ssi-client-application/frontend`: `npm test` or `npm run lint`
- `ssi-client-lib`: `npm run build`
- `ssi-wallet/mobile-app`: `npm test` or `npm run lint`

If you cannot run a check, say so clearly in the pull request.

## Documentation

Update documentation when you change:

- public endpoints,
- runtime flows,
- configuration,
- developer setup,
- module responsibilities.

## License

By contributing to this repository, you agree that your contributions are
licensed under the repository root `LICENSE` file unless a subdirectory
explicitly states otherwise.
