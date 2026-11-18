# needs the owner's hand

- ← [umbau] (2026-11-18 late): phase 0a ready but **needs the owner's hand**: the E2E workflow change is a patch in `tools/ci/pending/` (donald's token has no `workflow` scope – same wall as the laptop's web.yml patch); line under „Waiting for the owner“. Not blocking: `tools/ci/e2e_shards.sh` splits the full E2E run over 4 GitHub runs today (4 `ci/` refs), that is what the checkpoints use. Plan correction: e2e.yml caps a run at 45 min, so „45–85 min“ is run + queue time.
