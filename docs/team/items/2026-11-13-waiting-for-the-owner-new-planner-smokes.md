# [laptop] Waiting for the owner: new planner smokes in CI (web.yml)

- **[laptop] Waiting for the owner: new planner smokes in CI (web.yml)** – my GitHub token may not push workflow files (no `workflow` scope), so the change waits as `tools/ci/pending/web-yml-ci-smokes-2026-11-13.patch` (adds center + stop to cells, phonelink to areas-parity, sort + sync-rate to lang-gpx; ~7 machine-minutes, no longer wall time). Owner: `git am tools/ci/pending/web-yml-ci-smokes-2026-11-13.patch` on a branch and open the PR (or give the token the workflow scope); then delete the patch file.
