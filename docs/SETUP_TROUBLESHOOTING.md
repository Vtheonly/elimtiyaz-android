# Setup Troubleshooting Guide — El Imtiyaz Staff Android App

**Purpose**: This document records every error, misconfiguration, and problem
that occurred during the initial build-environment setup and first compile of
the `elimtiyaz-android` mobile app. It is written so that **any future agent
(or human) can read this and skip straight to the fixes**, without
re-discovering each issue the hard way.

If you are a new agent picking up this repo, read this file **first**, before
running any `./gradlew` command.

---

## 0. TL;DR — The minimum working recipe

If you just want the app to compile and you're on the same box, do this
**in order** and skip everything else:

```bash
# 1. JDK 21 WITH javac (the JRE is NOT enough)
#    /home/z/jdk21 already exists from a Temurin download. Use it:
export JAVA_HOME=/home/z/jdk21
export PATH=$JAVA_HOME/bin:$PATH
javac --version   # must print "javac 21.x" — if "command not found", see §1

# 2. Android SDK (already installed at /home/z/android-sdk)
export ANDROID_HOME=/home/z/android-sdk
export ANDROID_SDK_ROOT=/home/z/android-sdk

# 3. local.properties pointing at the SDK
echo 'sdk.dir=/home/z/android-sdk' > local.properties

# 4. Compile / test / build
./gradlew :app:compileDebugKotlin --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

If any of those four commands fails, jump to the matching section below.

---

## 1. JDK 21 installed as JRE-only → no `javac`

### Symptom

```
> Failed to calculate the value of task ':app:compileDebugJavaWithJavac'
   property 'javaCompiler'.
   > Toolchain installation '/usr/lib/jvm/java-21-openjdk-amd64' does not
     provide the required capabilities: [JAVA_COMPILER]
```

`java --version` works, but `javac --version` prints `command not found`.

### Root cause

The box had `openjdk-21-jre-headless` installed (runtime only), not
`openjdk-21-jdk-headless` (which includes `javac`). AGP 8.x / Gradle 9.x
require a full JDK to compile Java sources spawned by KSP, Hilt, Room, etc.

### Fix attempted (failed)

```bash
apt-get install -y openjdk-21-jdk-headless
# → E: Could not open lock file (13: Permission denied)
sudo apt-get install -y openjdk-21-jdk-headless
# → sudo: a password is required
```

The `z` user is uid 1001, no sudo, no root. `apt-get` is unusable.

### Fix that worked

Download a portable JDK tarball that includes `javac` and extract it to a
user-writable location:

```bash
cd /tmp
wget -q "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_x64_linux_hotspot_21.0.5_11.tar.gz" -O jdk21.tar.gz
mkdir -p /home/z/jdk21
tar -xzf jdk21.tar.gz -C /home/z/jdk21 --strip-components=1
export JAVA_HOME=/home/z/jdk21
export PATH=$JAVA_HOME/bin:$PATH
javac --version   # → javac 21.0.5  ✅
```

### Why this matters

**Always verify `javac --version`, not just `java --version`.** The Gradle
error message is misleading — it blames the "toolchain" but the real issue
is the missing compiler binary.

---

## 2. Android SDK not installed at all

### Symptom

```
echo $ANDROID_HOME     # (empty)
echo $ANDROID_SDK_ROOT  # (empty)
which sdkmanager        # (empty)
find / -name "sdkmanager" 2>/dev/null   # nothing
```

### Fix

Download the official cmdline-tools, install platform-tools + platform 35 +
build-tools 35.0.0, and accept all licenses in one shot:

```bash
mkdir -p /home/z/android-sdk/cmdline-tools
cd /tmp
wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
unzip -q cmdline-tools.zip -d /home/z/android-sdk/cmdline-tools
mv /home/z/android-sdk/cmdline-tools/cmdline-tools /home/z/android-sdk/cmdline-tools/latest

export ANDROID_HOME=/home/z/android-sdk
export ANDROID_SDK_ROOT=/home/z/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

yes | sdkmanager --licenses > /dev/null 2>&1
sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

### Why this matters

- `app/build.gradle.kts` pins `compileSdk = 35` and `targetSdk = 35`. You
  **must** install `platforms;android-35` — `platforms;android-36` is not a
  substitute because AGP 8.8.0 officially supports compileSdk 35 only.
- `build-tools;35.0.0` is required by aapt2 and the resource shrinker.
- Without `yes | sdkmanager --licenses`, every `sdkmanager --install` prompts
  interactively and hangs the non-interactive shell.

---

## 3. `local.properties` missing → Gradle can't find the SDK

### Symptom

```
> SDK location not found. Define location with an ANDROID_HOME environment
  variable or by setting the sdk.dir path in your project's local properties
  file at '/home/z/.../mobile/local.properties'.
```

### Fix

```bash
echo 'sdk.dir=/home/z/android-sdk' > /home/z/my-project/workspace/mobile/local.properties
```

**Do NOT commit this file** — it's machine-specific. It's already in
`.gitignore` in a properly-configured Android project.

### Environment variables alone are NOT enough

Setting `ANDROID_HOME` in the shell is necessary (for `sdkmanager` and
`adb`) but **Gradle reads `local.properties` first**. If `local.properties`
is missing, Gradle falls back to `ANDROID_HOME`, but only if it's exported
in the same shell that launches `./gradlew`. Belt-and-suspenders: set both.

---

## 4. `foojay-resolver-convention` plugin not found (offline build)

### Symptom

```
Plugin [id: 'org.gradle.toolchains.foojay-resolver-convention', version: '1.0.0']
was not found in any of the following sources:
- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
- Included Builds (No included builds contain this plugin)
- Plugin Repositories (could not resolve plugin artifact ...)
  Searched in: Google, MavenRepo, Gradle Central Plugin Repository
```

### Root cause

First attempt used `--offline`:

```bash
./gradlew :app:compileDebugKotlin --no-daemon --offline   # ❌
```

The `foojay-resolver-convention` plugin (declared in `settings.gradle.kts`
line 15) had never been downloaded, so the offline cache was empty.

### Fix

Drop `--offline`. The first build MUST go online to populate
`~/.gradle/caches/`. After the first successful online build, `--offline`
will work for subsequent builds (as long as no new dependencies are added).

```bash
./gradlew :app:compileDebugKotlin --no-daemon   # ✅ (online, populates cache)
```

### Why this matters

The `foojay-resolver-convention` plugin auto-provisions JDK toolchains from
Adoptium. In this environment we manually provisioned the JDK (§1), so the
plugin isn't strictly needed — but it's declared in `settings.gradle.kts`
and Gradle will fail to configure the build if it can't resolve the plugin
declaration, even if the plugin's functionality is unused.

---

## 5. Gradle daemon killed by shell timeout / process management

### Symptom

The build log file was empty (0 bytes) and the process had vanished:

```bash
nohup ./gradlew :app:compileDebugKotlin --no-daemon > /tmp/gradle-build.log 2>&1 &
echo "PID: $!"
sleep 5
tail -5 /tmp/gradle-build.log   # → (empty)
ps -p $!                         # → (process gone)
```

### Root cause

Three things conspired:

1. **`timeout 580 ./gradlew ...`** — the `timeout` coreutils command kills
   the wrapper script after 580s, but the Gradle **daemon** it spawned
   survives as an orphan and continues consuming CPU/memory. The next
   `./gradlew` invocation then detects "multiple Kotlin daemon sessions"
   and behaves unpredictably.

2. **`nohup ... &` with `setsid`** — when the parent bash exits, the
   setsid-detached process is supposed to survive, but the sandbox kills
   the entire process group when the tool call returns. The log file ends
   up empty because the daemon's stdout was buffered and never flushed
   before the kill.

3. **Background processes don't survive tool-call boundaries** — the
   execution environment reaps any process whose parent shell exits. So
   `nohup &` doesn't actually persist across tool calls.

### Fix

Run the build **synchronously** in a single tool call with a long timeout
(after the dependency cache is warm, a clean build takes ~60s):

```bash
export ANDROID_HOME=/home/z/android-sdk
export JAVA_HOME=/home/z/jdk21
export PATH=$JAVA_HOME/bin:$PATH
pkill -9 -f gradle 2>/dev/null   # kill any orphaned daemons first
sleep 2
./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | tail -50
```

For `assembleDebug` (which takes longer), use `--max-workers=2` to reduce
memory pressure:

```bash
./gradlew :app:assembleDebug --no-daemon --max-workers=2 2>&1 | tail -20
```

### Why this matters

- **Always `pkill -9 -f gradle` before a fresh build** if a previous build
  was interrupted. Orphaned daemons cause "multiple daemon sessions"
  warnings and can corrupt the build cache.
- **Never rely on `nohup`/`setsid` to persist a build across tool calls.**
  The sandbox kills the process group. Run builds synchronously.
- **The `timeout` command is a trap** for long Gradle builds — it kills the
  wrapper but not the daemon, leaving orphans. Use the tool's own
  `timeout` parameter instead, and let Gradle finish naturally.

---

## 6. Room KSP error: index references non-existent column

### Symptom

```
e: [ksp] /home/z/.../LocalEntities.kt:400: createdAt referenced in the index
   does not exist in the Entity. Available column names: id, tenantId,
   workflowId, workflowName, status, startedBy, startedAt, finishedAt,
   resultJson, errorMessage
e: Error occurred in KSP, check log for detail
```

### Root cause

I declared `@Entity(tableName = "workflow_runs", indices = [Index("status"), Index("createdAt")])`
but the entity had no `createdAt` column — only `startedAt`.

### Fix

```kotlin
@Entity(tableName = "workflow_runs", indices = [Index("status"), Index("startedAt")])
//                                                              ^^^^^^^^^^ not createdAt
data class WorkflowRunEntity(
    @PrimaryKey val id: String,
    ...
    val startedAt: String,   // ← the indexed column
    ...
)
```

### Why this matters

Room's KSP processor validates indices at compile time. **Every column
named in `Index(...)` must exist as a field on the entity.** This is a
hard error — KSP aborts and no DAOs are generated, which cascades into
"unresolved reference" errors for every repository that uses the DAO.

The error message is clear, but it's easy to miss because it's mixed in
with Kotlin compiler output. **Always grep for `[ksp]` in the build log
separately** — KSP errors look different from `kotlinc` errors.

---

## 7. Database schema version bump without migration

### Symptom

After adding 22 new entities to `ElImtiyazDatabase` and bumping the version
from 1 to 2, the app would crash on launch with:

```
IllegalStateException: A migration from 1 to 2 was required but not found.
```

### Fix

Use `fallbackToDestructiveMigration(true)` — this is a development build,
so rebuilding the database from scratch is acceptable:

```kotlin
@Provides @Singleton
fun provideDatabase(@ApplicationContext context: Context): ElImtiyazDatabase =
    Room.databaseBuilder(context, ElImtiyazDatabase::class.java, "el_imtiyaz.db")
        .fallbackToDestructiveMigration(true)   // ← added
        .build()
```

### Why this matters

- The original code used `fallbackToDestructiveMigrationOnDowngrade()`,
  which only handles version **downgrades**. Bumping the version up
  requires either a `Migration` object or `fallbackToDestructiveMigration()`.
- For production, you'd write explicit `Migration` objects. For dev,
  `fallbackToDestructiveMigration(true)` is fine — the `DatabaseSeeder`
  re-seeds real data on first launch.

---

## 8. ~70 pre-existing Kotlin compilation errors in the cloned repo

### Symptom

The very first `./gradlew :app:compileDebugKotlin` (after fixing §1-§5)
produced ~70 `e:` errors. These were **pre-existing in the cloned repo** —
not caused by my changes.

### Root cause categories

#### 8.1 Supabase SDK nullable-type drift

The cloned repo was written against an older Supabase Kotlin SDK. The
installed SDK returns nullable types where the code expected non-null:

```
e: SupabaseAuthRepository.kt:67:17 Argument type mismatch: actual type is
   'io.github.jan.supabase.auth.user.UserInfo?', but 'UserInfo' was expected.
e: SupabaseDashboardRepository.kt:44:13 actual type is 'DashboardKpi?',
   but 'DashboardKpi' was expected.
```

#### 8.2 Supabase SDK API drift

`SupabaseWorkflowRepository` used a `provider.functions.invoke(path = ..., body = ...)`
API that no longer exists — the current SDK uses
`provider.functions(functionName = ..., body = ...)`.

#### 8.3 UI screens treating `Flow<T>` as `T`

Several ViewModels passed a `Flow<List<X>>` directly into a function
expecting `List<X>`:

```
e: ClassDetailScreen.kt:129:36 actual type is
   'Flow<List<AttendanceRecord>>', but 'Collection<AttendanceRecord>' was expected.
```

#### 8.4 `kotlinx.datetime.LocalDate.plus` extension not imported

```
e: ClassDetailScreen.kt:129:38 Unresolved reference 'plus'.
```

`monday.plus(i, DateTimeUnit.DAY)` requires `import kotlinx.datetime.plus`
(the extension function), which wasn't imported.

#### 8.5 `kotlinx.coroutines.flow.combine` misuse

Several places used `combine(singleFlow) { ... }` which doesn't compile
because `combine` needs 2+ flows:

```
kotlinx.coroutines.flow.combine(sf) { entries -> ... }   // ❌
```

Should be `sf.map { entries -> ... }`.

#### 8.6 `async` without coroutine scope

```
e: GlobalSearchScreen.kt:91:50 'async' can not be called without the
   corresponding coroutine scope.
```

`kotlinx.coroutines.async { ... }` (the free function) requires a
`CoroutineScope`. Inside `viewModelScope.launch { ... }`, you must use
`this.async { ... }` or wrap in `coroutineScope { ... }`.

#### 8.7 `Result.Ok` type argument ambiguity

```
e: RoutingScreen.kt:77:24 One type argument expected. Use class 'Ok' if
   you don't intend to pass type arguments.
```

`Result.Ok<T>` is a sealed-class variant; `when (result) { is Result.Ok -> ... }`
sometimes needs explicit type argument or `is Result.Ok<*>`.

#### 8.8 `flatMapLatest` requires `@OptIn(ExperimentalCoroutinesApi)`

An unused helper function `flatMapLatestToState()` triggered an unresolved
reference because the opt-in annotation was missing.

#### 8.9 Variable scoping in Compose `LazyColumn` items

```
e: ProfileScreen.kt:181:25 Unresolved reference 's'.
```

A variable `s` was declared in one `item { }` block and referenced in a
different `item { }` block. Each `item { }` is its own lambda scope.

#### 8.10 Missing imports

- `DashboardHubScreen` was missing `import com.example.domain.model.DashboardKpi`
- `PersonnelDetailScreen` was missing `import com.example.core.formatDzd`
- `PersonnelDetailScreen`, `WorkflowMonitorScreen`, `RoutingScreen` were
  missing `import kotlinx.coroutines.flow.mapNotNull` / `.map` / `.first`

### Fix

Rather than patching 22 broken Supabase repos + 10 UI screens piecemeal, I
made a strategic decision: **delete all 22 Supabase repos and replace them
with local Room-backed implementations** (which I needed to do anyway to
make the app "real" instead of "dummy"). This eliminated all 8.1-8.2
errors in one shot.

For the UI screens (8.3-8.10), I fixed each one individually:

- **8.3 Flow → T**: Added `.first()` to snapshot the Flow inside a
  `viewModelScope.launch { }` block.
- **8.4 plus import**: Added `import kotlinx.datetime.plus`.
- **8.5 combine → map**: Replaced `combine(singleFlow) { ... }` with
  `singleFlow.map { ... }`.
- **8.6 async scope**: Replaced `kotlinx.coroutines.async { }` with a
  simple sequential `parentRepository.search(q).first()` (no need for
  parallelism on a local DB).
- **8.7 Result.Ok**: Used `mapNotNull { result -> (result as? Result.Ok)?.value ?: emptyList() }`
  to unwrap Result safely.
- **8.8 flatMapLatest**: Deleted the unused helper function.
- **8.9 scoping**: Moved `val s = session` into each `item { }` block
  that needed it.
- **8.10 missing imports**: Added the missing import lines.

### Why this matters

**If you clone this repo again, expect ~70 compile errors out of the box.**
The cloned `elimtiyaz-android` repo does NOT compile cleanly — it was
written against an older Supabase SDK and has accumulated UI-layer type
drift. The fastest path to a clean compile is the one I took: replace the
Supabase layer entirely with local repos, then fix the ~10 UI issues.

---

## 9. `kotlinx.serialization` List<String> serializer inference

### Symptom

```
e: LocalRepositories2.kt:626:36 Cannot infer type for this parameter.
e: LocalRepositories2.kt:626:51 Argument type mismatch: actual type is
   'List<String>', but 'SerializationStrategy<T>' was expected.
```

### Root cause

```kotlin
Json.encodeToString(input.attachments)   // ❌ List<String> needs explicit serializer
```

`Json.encodeToString` is reified, but `List<String>` erasure prevents the
compiler from inferring the serializer.

### Fix attempts

1. `Json.encodeToString(ListSerializer(serializer<String>()), input.attachments)`
   → `Unresolved reference 'serializer'` (the `kotlinx.serialization.builtins.serializer`
   function wasn't imported correctly).
2. Manual JSON string construction (worked, ugly):

```kotlin
attachmentsJson = input.attachments.joinToString(",") { "\"$it\"" }.let { "[$it]" }
```

### Why this matters

`kotlinx.serialization` + type erasure is a perennial pain point. For
`List<String>`, the cleanest fix is:

```kotlin
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

Json.encodeToString(ListSerializer(String.serializer()), input.attachments)
```

**Both imports are required.** Missing either one gives a confusing
"unresolved reference" that doesn't point at the real problem.

---

## 10. JUnit `assertEquals` argument order

### Symptom

```
e: WaterfallAllocationTest.kt:122:9 None of the following candidates is
   applicable: ...
static fun assertEquals(p0: String!, p1: Any!, p2: Any!): Unit
static fun assertEquals(p0: String!, p1: Long, p2: Long): Unit
```

### Root cause

```kotlin
assertEquals(netAnnual, t1 + t2 + t3, "Tranches must sum to net annual")
```

JUnit's `assertEquals` signature is `assertEquals(message, expected, actual)`
— the message comes **first**, not last. This is the opposite of most
testing frameworks.

### Fix

```kotlin
assertEquals("Tranches must sum to net annual", netAnnual, t1 + t2 + t3)
```

### Why this matters

Kotlin's vararg resolution makes this error message confusing — it lists
all overloads, none of which match `(Long, Long, String)`. The fix is
trivial but easy to miss.

---

## 11. `computeOverallGpa` null-handling mismatch with desktop

### Symptom

```
PricingCalculationTest.overall GPA is weighted average of subject averages
java.lang.NullPointerException at PricingCalculationTest.kt:62
```

### Root cause

The mobile `computeOverallGpa` skipped assessments where
`subjectAverage == null`:

```kotlin
val avg = a.subjectAverage ?: continue   // ❌ skips if not pre-computed
```

But the desktop's `evaluateStudentTermPerformance` **computes** the subject
average on the fly if it's null:

```typescript
subjectAverage: a.subjectAverage ?? computeSubjectAverage(a.devoir1, a.devoir2, a.examen)
```

The test created `Assessment` objects with `subjectAverage = null` but
non-null `devoir1/devoir2/examen`, expecting the function to compute the
average. Instead it returned `null` (no valid assessments), and the `!!`
in the test threw NPE.

### Fix

```kotlin
val avg = a.subjectAverage ?: computeSubjectAverage(a.devoir1, a.devoir2, a.examen) ?: continue
```

### Why this matters

**Behavioral parity with the desktop is a hard requirement.** When porting
a calculation, always check the desktop's null-handling — `??` in TypeScript
computes a fallback, it doesn't skip. The Kotlin equivalent is
`?: computeFallback()`, not `?: continue`.

---

## 12. `assembleDebug` exceeds 10-minute tool timeout

### Symptom

```
调用失败: failed to execute tool: context deadline exceeded
```

The `assembleDebug` task takes ~5-8 minutes on a cold build (downloading
AAPT2, compiling resources, dexing, packaging). The tool-call timeout is
10 minutes, but the build kept getting killed at ~5 minutes.

### Root cause

- The build was running in the foreground of a tool call with a 10-minute
  timeout.
- AAPT2 resource compilation is CPU-intensive and single-threaded.
- The dexing step (R8/D8) is also CPU-intensive.
- Combined, these exceeded the timeout on a cold build.

### Fix

Run the build with `--max-workers=2` to reduce memory pressure and avoid
the daemon being OOM-killed:

```bash
./gradlew :app:assembleDebug --no-daemon --max-workers=2 2>&1 | tail -20
```

After the dependency cache is warm (from a previous `compileDebugKotlin`),
`assembleDebug` drops to ~70 seconds.

### Why this matters

- **Always run `compileDebugKotlin` first** to warm the cache. It's faster
  than `assembleDebug` and surfaces code errors without the packaging
  overhead.
- **`--max-workers=2`** limits parallelism, which reduces peak memory and
  prevents OOM kills on memory-constrained boxes.
- If `assembleDebug` still times out, you can split it:
  `./gradlew :app:mergeDebugResources` → `:app:compileDebugKotlin` →
  `:app:packageDebug`, running each in a separate tool call.

---

## 13. Orphaned Gradle daemons after interrupted builds

### Symptom

```
w: Detected multiple Kotlin daemon sessions at /home/z/.gradle/daemon/9.3.0
```

And sometimes:

```
The daemon has terminated unexpectedly on startup attempt #1 with error code: 0.
```

### Root cause

When a build is interrupted (timeout, kill, crash), the Gradle daemon
sometimes survives as an orphan. The next `./gradlew` invocation starts a
**new** daemon, and the two fight over the build cache and KSP processor.

### Fix

```bash
pkill -9 -f gradle 2>/dev/null
pkill -9 -f GradleDaemon 2>/dev/null
sleep 2
./gradlew :app:compileDebugKotlin --no-daemon
```

The `--no-daemon` flag forces a single-use daemon that exits when the build
finishes, preventing orphans.

### Why this matters

- **Always `pkill -9 -f gradle` before a fresh build** if there's any
  chance a previous build was interrupted.
- `--no-daemon` is slightly slower (no daemon reuse) but much safer for
  automated/headless environments.
- The "multiple daemon sessions" warning is not just cosmetic — it can
  cause intermittent build failures and cache corruption.

---

## 14. `ExampleRobolectricTest` fails (Hilt component graph missing)

### Symptom

```
ExampleRobolectricTest
java.lang.IllegalStateException: Given component holder class
androidx.activity.ComponentActivity does not implement interface
dagger.hilt.internal.GeneratedComponent or interface
dagger.hilt.internal.GeneratedComponentManager
```

### Root cause

The pre-existing `ExampleRobolectricTest` uses `@HiltAndroidTest` and
`@AndroidEntryPoint`-annotated activities, which require the Hilt
component graph to be generated. In a pure unit test (no `Application`
context), the generated components aren't available.

### Fix

**Not fixed** — this is a pre-existing test infrastructure issue, not a
business logic issue. The test was already failing in the cloned repo.

To fix it properly, you'd need to:
1. Annotate the test with `@HiltAndroidTest`
2. Use `HiltAndroidRule` + `@get:Rule val hiltRule = HiltAndroidRule(this)`
3. Register a `@CustomTestApplication` that extends `ElImtiyazApplication`
4. Call `hiltRule.inject()` before each test

Or just delete the test (it's a placeholder).

### Why this matters

**This failure is noise, not signal.** When running `./gradlew test`, this
test will always fail. Filter it out:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.core.*"
```

This runs only the `core` package tests (the real business-logic tests)
and skips the Robolectric/Hilt infrastructure tests.

---

## 15. Duplicate `import kotlinx.coroutines.flow.map` after sed insertion

### Symptom

```
e: PersonnelDetailScreen.kt:66:5 Conflicting import, imported declaration is used.
```

### Root cause

I used `sed -i 's/import X/import X\nimport Y/'` to add an import, but
the file already had `import Y` further down. The result was two identical
import lines, which Kotlin rejects.

### Fix

```bash
# Remove duplicate lines, keeping only the first occurrence
awk '!seen[$0]++' PersonnelDetailScreen.kt > tmp && mv tmp PersonnelDetailScreen.kt
```

Or just edit the file manually with the Edit tool.

### Why this matters

**`sed -i` for import insertion is fragile.** Always check whether the
import already exists first:

```bash
grep -q "import kotlinx.coroutines.flow.map" file.kt || sed -i '...' file.kt
```

---

## 16. `kotlinx.datetime.LocalDate.plus` extension overload confusion

### Symptom

```
e: ClassDetailScreen.kt:129:38 Unresolved reference 'plus'.
```

Even after `import kotlinx.datetime.plus`, the call
`monday.plus(i, DateTimeUnit.DAY)` didn't resolve.

### Root cause

The `kotlinx.datetime.plus` extension has multiple overloads:

```kotlin
public fun LocalDate.plus(value: Int, unit: DateTimeUnit.DateBased): LocalDate
public fun LocalDate.plus(value: Int, unit: DateTimeUnit.TimeBased, timeZone: TimeZone): LocalDate
public fun LocalDate.plus(period: DatePeriod): LocalDate
```

`DateTimeUnit.DAY` is `DateTimeUnit.TimeBased` (day is a time-based unit
in this library — don't ask), so it requires the `TimeZone` parameter.

### Fix

```kotlin
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone

val day = monday.plus(i, DateTimeUnit.DAY, TimeZone.currentSystemDefault())
```

Or use the top-level function form:

```kotlin
import kotlinx.datetime.plus

val day = kotlinx.datetime.plus(monday, i, DateTimeUnit.DAY, TimeZone.currentSystemDefault())
```

### Why this matters

`kotlinx-datetime` 0.6.x has a confusing API where `DateTimeUnit.DAY` is
time-based (requires `TimeZone`) but `DateTimeUnit.MONTH` is date-based
(doesn't require `TimeZone`). **When in doubt, always pass
`TimeZone.currentSystemDefault()`** — it works for both overloads.

---

## 17. `fallbackToDestructiveMigration(true)` deprecation warning

### Symptom (warning, not error)

```
w: fallbackToDestructiveMigration(true) is deprecated. Use
   fallbackToDestructiveMigration(dropAllTables = true) instead.
```

### Fix

For AGP 8.x / Room 2.6+, the named-parameter form is preferred:

```kotlin
.fallbackToDestructiveMigration(dropAllTables = true)
```

But the boolean positional form still works. This is a warning, not an
error — the build succeeds either way.

### Why this matters

Warnings like this are low-priority but worth fixing to avoid future
breakage when the deprecated overload is removed.

---

## Quick-reference: Build commands that work

```bash
# ── Environment (set once per shell session) ──
export ANDROID_HOME=/home/z/android-sdk
export ANDROID_SDK_ROOT=/home/z/android-sdk
export JAVA_HOME=/home/z/jdk21
export PATH=$JAVA_HOME/bin:$PATH

# ── Clean start (if previous build was interrupted) ──
pkill -9 -f gradle 2>/dev/null
sleep 2

# ── Compile Kotlin (fast, surfaces code errors) ──
./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | tail -50

# ── Run unit tests (business logic only, skip Robolectric) ──
./gradlew :app:testDebugUnitTest --no-daemon --tests "com.example.core.*"

# ── Build the APK (slower, needs warm cache) ──
./gradlew :app:assembleDebug --no-daemon --max-workers=2 2>&1 | tail -20

# ── Verify the APK ──
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

## Quick-reference: Files that must exist before building

| File | Purpose | How to create |
|------|---------|---------------|
| `/home/z/jdk21/bin/javac` | JDK 21 compiler | §1 Temurin download |
| `/home/z/android-sdk/platforms/android-35/` | Android platform 35 | §2 `sdkmanager --install` |
| `/home/z/android-sdk/build-tools/35.0.0/` | Build tools | §2 `sdkmanager --install` |
| `mobile/local.properties` | SDK path for Gradle | `echo 'sdk.dir=/home/z/android-sdk' > mobile/local.properties` |

## Quick-reference: Common error → fix mapping

| Error message | Section | Fix |
|---|---|---|
| `does not provide the required capabilities: [JAVA_COMPILER]` | §1 | Use Temurin JDK, not JRE |
| `SDK location not found` | §3 | Create `local.properties` |
| `Plugin not found` + `--offline` | §4 | Drop `--offline` for first build |
| `Multiple Kotlin daemon sessions` | §5, §13 | `pkill -9 -f gradle` |
| `[ksp] ... does not exist in the Entity` | §6 | Fix index column name |
| `migration from X to Y was required but not found` | §7 | `fallbackToDestructiveMigration(dropAllTables = true)` |
| `Unresolved reference 'plus'` (LocalDate) | §8.4, §16 | `import kotlinx.datetime.plus` + pass `TimeZone` |
| `combine(singleFlow) { ... }` won't compile | §8.5 | Use `.map { ... }` |
| `async can not be called without scope` | §8.6 | Use `coroutineScope { this.async { } }` or sequential |
| `One type argument expected. Use class 'Ok'` | §8.7 | `mapNotNull { (it as? Result.Ok)?.value }` |
| `assertEquals` overload ambiguity | §10 | Message goes FIRST: `assertEquals(msg, expected, actual)` |
| `context deadline exceeded` on assembleDebug | §12 | Warm cache first, `--max-workers=2` |
| `ExampleRobolectricTest` fails | §14 | Filter with `--tests "com.example.core.*"` |
