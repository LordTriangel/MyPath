# Tour suggestions — feedback captured, not scheduled (2026-11-08):

- **Tour suggestions — feedback captured, not scheduled (2026-11-08):** expand the existing idea using
  [bike loops from the doorstep](https://github.com/happychriss/OnPath/discussions/12) and the same contributor's
  [nearby hiking loops](https://github.com/happychriss/OnPath/discussions/4). Feature request, not a defect in the
  current round-trip mode: suggest loops without requiring a destination first. **Later:** robustness, the Play
  launch and feedback from a small real-world user group come first; no implementation date or engine choice.
  - [ ] Evaluate one shared loop-suggestion mechanism with activity-specific starts: current/chosen point for cycling;
    suitable nearby trailheads (e.g. parking or bus stops) for hiking.
  - [ ] Define a small on-device experiment: target distance + existing activity profile, several spatially distinct
    candidates, varied suggestions between requests, and a soft overlap penalty (avoid excessive detours).
    Compare candidate generation through the existing RoutingEngine with other feasible BRouter approaches;
    no assumption that a suitable round-trip API is already available.
  - [ ] Assess initial ranking: distance fit, profile-compatible surface/access, signed routes, low traffic,
    manageable ascent and route warnings; check actual route suitability rather than relying on the profile alone.
    Hiking also needs a clear rule for areas without downloaded maps.
  - [ ] Before implementation, measure phone runtime, cancellation and offline behaviour; preview candidates with
    distance, ascent, surface and a map, then open the selected route in the existing planner.
    Contributor `mrtnha` offered to test; follow up in the source discussion when a prototype is available.
  Triage outcome: one backlog item for both discussions; public reply remains a draft pending owner review.
