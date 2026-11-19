# Questions the roles answered themselves in the night 19→20 Nov (for the owner's morning)

One line each: `- Q … → A … (role, time)`. The owner reads this in the morning and may overrule any answer.

- Q Planner piece by Friday? → A owner chose „push the visible piece through“ (owner, 22:50)
- Q Why had the planner's place filter no test? → A search-smoke checked the chips only with the local index, where the answer comes at once; a late online answer and a slow engine were never simulated. Now: an engine test for a late online answer without the chip (red before the fix), a smoke step with the real Photon answer for „München“, and one with the engine held 3 s (the chip must light up and filter within 100 ms). Rule for tonight: every planner bug gets its timing case too (laptop, 02:30)
- Q Browser icon from the onpath.earth site? → A that repo is outside the laptop's access; the planner's icon is the app's launcher icon (mountain + path on #1F6E43), SVG + 32 px + apple-touch – the same mark as on the phone (laptop, 02:30)
- Q: The test clean-up keeps every check and so saves only 755 lines – cut more now by dropping layout / colour pins and thinning screenshots? → A: no; that is a product decision, not for a night before a release. The candidates come as a list after the release. (umbau)
- Q: Five CONVERT groups (G02, G03, G14, G16, G18) and 20 DROP files would lose a check or save nothing – force them anyway? → A: no; left as they are, reasons in `docs/design/test-audit.tsv`. (umbau)
- Q: A comment in six production files still named deleted test classes – may comments in `src/main` be corrected in a test clean-up? → A: yes, comment-only lines, five files; the sixth (`SheetRise.kt`) is watched by the planner's parity and stays until the laptop looks at that file anyway. (umbau)
