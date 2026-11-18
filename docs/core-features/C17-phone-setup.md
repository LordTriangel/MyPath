# C17 – Phone setup


Part of the core features (`docs/core-features.md`): every core feature has JVM regression tests; a field bug first gets a failing test, then the fix.


| # | Core feature | Field bugs guarded against | Regression tests |
| --- | --- | --- | --- |
| C17 | **Phone setup before a tour** (owner, `docs/design/onboarding-phone-check.md`) – no system dialog at the first screen (a welcome page: Set up now / Later); location is asked only from the guide after its explanation (fine + coarse together; approximate → the "precise" step; after Android stops asking → App info with the path); *Start navigation* / *Start recording* (no route) / *Continue* run the check: precise location + location on are required (the tour starts when the guide ends with them), notifications, vibration felt, battery not restricted, brand steps and battery saver are recommended ("Later" = quiet for 7 days); every return to the app re-reads the phone and skips what is fixed; what Android's unused-app reset removed is said calmly; Settings → Phone setup shows every check with its state | – | `PhoneCheckTest`, `SettingsSummaryTest`, E2E `PhoneSetupE2ETest`, `UxReviewFixesTest.firstStartDialogsComeOneAfterTheOther`, `SettingsSummaryTest.phoneSetupSummary`, screenshots `SetupScreenshot*Test` (EN, DE, font 2.0); emulator `PhoneSetupE2ETest`; the voice step on any engine (field bug 2026-11-07, Samsung S10: a silent Test after "no voice") `VoiceSetupTest.aTapAfterNoVoiceChecksAgain_neverSilent` + engine / advice rules, screenshots `VoiceStepScreenshot*Test` (EN, DE, font 2.0) |
