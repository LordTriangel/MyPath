# Bike difficulty limit S0 / S1 / S2 (owner 2026-11-01, "easy to build?"):

- **Bike difficulty limit S0 / S1 / S2 (owner 2026-11-01, "easy to build?"):** today only the hike has a chosen limit
  (T1–T5). Gravel (`trekking.brf`) and Bike / E-bike (`touring.brf`) already make S2 very expensive (+30) and shut S3+
  (10000); `mtb.brf` only adds a cost per S grade, no limit. Wanted: a chosen limit per bike activity ("nur S0", "bis S1"),
  like the T level. Size (donald estimate): medium, not a one-liner – a profile variable in the three .brf (like the
  hike's T limit), the picker line + stored level (`LastActivity`), `RoutingContract` / `EngineProvider` / `PlanResult`
  pass it (shared with the web: picker + `PlanGpx` sport type), `RoutingFingerprint` + the cells' router-class hash
  (`RouterClassCheck`) change, `ActivityLayers` maybe shows the band by S level. Most ways carry no `mtb:scale` – the
  limit acts only where mappers set it (MTB areas yes, gravel roads mostly not). After Navigation neu. [donald + laptop]
