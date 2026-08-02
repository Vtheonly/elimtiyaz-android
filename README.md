# El-Imtiyaz Android

> **Documentation has moved.** All project history, restoration reports,
> architecture, and planning documents now live in
> [`restore-reports-docs/`](restore-reports-docs/README.md).

The El-Imtiyaz Android app is the mobile companion to the
[El-Imtiyaz Desktop platform](https://github.com/Vtheonly/AgentGithubUplaod)
— a private school management system for an Algerian private school.

## Quick Start

```bash
# 1. Set up local.properties (point to your Android SDK)
echo "sdk.dir=/path/to/your/Android/Sdk" > local.properties

# 2. Configure Supabase credentials (or use demo fallback)
cp .env.example .env
# Edit .env: SUPABASE_URL=...  SUPABASE_ANON_KEY=...

# 3. Compile
./gradlew :app:compileDebugKotlin

# 4. Run unit tests
./gradlew :app:testDebugUnitTest

# 5. Build debug APK
./gradlew :app:assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

## Documentation

**Start here:** [`restore-reports-docs/README.md`](restore-reports-docs/README.md)

Key documents:

- [Project Overview](restore-reports-docs/project-overview.md) — why this
  project exists, the two-repo setup.
- [Architecture](restore-reports-docs/architecture.md) — folder structure,
  module responsibilities, data flow.
- [Current Status](restore-reports-docs/current-status.md) — what works
  today, what's broken, technical debt.
- [Iteration History](restore-reports-docs/iteration-history.md) —
  engineering journal of all 3 restoration iterations.
- [Next Steps](restore-reports-docs/next-steps.md) — prioritized roadmap.

## Build Status

- ✅ `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL
- ✅ `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL (28 MB APK)
- ✅ 98/100 unit tests pass
- 🟡 11 repositories need SyncSupport migration (see
  [known-issues.md](restore-reports-docs/known-issues.md))
- 🟡 36 screens need design-system migration (see
  [current-status.md](restore-reports-docs/current-status.md))

## Repositories

- **Mobile (this repo):** https://github.com/Vtheonly/elimtiyaz-android
- **Desktop (source of truth):** https://github.com/Vtheonly/AgentGithubUplaod
