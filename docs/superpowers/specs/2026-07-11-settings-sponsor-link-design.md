# Settings Sponsor Link Design

## Goal

Give users a direct, unobtrusive way to sponsor KGS Calendar from the main Settings screen.

## User Experience

- Add a `Sponsor this project` settings row immediately above `Report a bug`.
- Tapping the row opens `https://github.com/sponsors/KGS501` in the user's browser.
- The row uses the existing external-link settings-row treatment and a localized title and summary.
- English and German resources are provided.

## Technical Design

- Keep the sponsor URL as a private constant next to the existing privacy-policy and bug-report URLs.
- Reuse the established `SettingsMenuRow` and `ACTION_VIEW` intent pattern used by `Report a bug`.
- No persistence, permissions, network calls, or in-app web view are introduced.

## Verification

- Add a focused unit test for the sponsor URL constant or extracted link target, if the UI architecture exposes it for tests.
- Build and run the existing unit-test suite.
- Verify in the Settings screen that the new row is directly above `Report a bug` and opens the correct GitHub Sponsors page.
