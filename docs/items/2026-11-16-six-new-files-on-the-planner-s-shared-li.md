# six new files on the planner's shared list

- → [laptop] (IR 2026-11-16): **six new files on the planner's shared list** (`app/web-shared-files.txt`: `suggest/` LongIntMap, WayKinds, WayNet, NetReader, NetSearch, RouterProfile – the tour-suggestions core: compact way network from rd5 + graph searches; not called by WebApi yet). `SharedFilesRuleTest` now allows all `btools.` packages (NetReader uses BRouter's own rd5 decoding, `btools.mapaccess`). A trial export compiled to Wasm (+71 KB); please check the planner build stays as small while nothing calls it, and tell IR if TeaVM objects.
