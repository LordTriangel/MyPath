# [donald] Beta bug button (owner 2026-11-10, chat) – step 1 only, for the owner's own phone:

- **[donald] Beta bug button (owner 2026-11-10, chat) – step 1 only, for the owner's own phone:** a small round button
  always on top (bottom left, over every screen, menu, sheet and dialog), can be dragged aside if it is in the way.
  A tap: (1) a screenshot of the current screen *without* the button; (2) the microphone starts and the owner speaks
  the problem (stop with a second tap); (3) a diagnostics snapshot of the current state that also holds the **last
  actions** (taps, mode changes, point edits – not only errors), so there is enough context. All three go into one
  folder per report on the phone; read out over USB (`tools/dev/device.sh pixel -- …`, a pull script). Build on what
  exists: `debug/EventLog` (the action trail – check that user actions are logged, add the missing ones) and
  `debug/Diagnostics` (the snapshot / zip). Captain's notes: voice as an audio file (needs the RECORD_AUDIO
  permission – new; Android's speech-to-text usually goes through Google, so no transcription on the phone –
  transcribe on the computer later); the button only in beta / debug builds or behind a Settings switch – sketch +
  the owner's word before building. **Later, not now:** users export a report and send it by e-mail.
