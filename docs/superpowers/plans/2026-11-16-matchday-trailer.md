# Matchday Trailer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One management action renders a YouTube-ready MP4 trailer for a matchday or playoff round from published picks, gallery images and a music library.

**Architecture:**
- Playwright renders each scene as PNG layers through the existing `SandboxedHtmlRenderer`.
- An ffmpeg child process composes the layers into motion and mixes the music.
- Car cutouts come from an ONNX segmentation model running in-process.
- A single in-memory job executor serialises trailer renders and cutouts, and is abandoned on restore.

**Tech Stack:** Java 25, Spring Boot 4, Thymeleaf, Playwright, ffmpeg/ffprobe (Ubuntu Noble package), ONNX Runtime for Java, Flyway (H2 + MariaDB), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-11-16-matchday-trailer-design.md`

## Global Constraints

- Branch: `feature/matchday-trailer`, branched from `origin/master` at `76d11db8`. Each delivery is one PR to `master`, created with `gh pr create --assignee jegr78`. Rebase onto `origin/master` before each PR.
- Flyway:
  - delivery 1 adds V48, delivery 2 adds V49
  - never edit V1 to V47
  - enumerated checks use CASE branches
- Backup schema: delivery 1 sets `SCHEMA_VERSION = 26`, delivery 2 sets `27`. `BackupSchemaTopologyIT` expects 54 entities after delivery 1.
- Coverage stays at or above 82 % line coverage. SpotBugs, FindSecBugs, Checkstyle (unused imports) and CodeQL must stay green.
- Tests:
  - Given-When-Then names with `// given`, `// when`, `// then`.
  - `*IT.java` carries `@Tag("integration")`; Playwright tests under `org.ctc.e2e` carry `@Tag("e2e")`.
  - No conditional skips. Every discovered test must run in its CI partition.
- Controllers stay thin and bind Form DTOs. Templates hold no logic.
- No inline styles on `.btn`. Every control is labelled and works without JavaScript, with CSRF.
- External commands (`ffmpeg`, `ffprobe`) run through `ProcessBuilder` with an argument list and never through a shell. Inputs are application-generated paths only.
- User-supplied values in log lines go through `LogSanitizer.sanitize()`.
- Comments: none, except a single line for a non-obvious reason. No phase or task markers in source.
- Local checks use `./scripts/test-feedback.py` only. Integration selectors need `--unit`. Never run a full local build; the complete CI run on the PR is authoritative.
- Each delivery updates `CLAUDE.md`, `README.md` and the GitHub wiki.
- Visual changes are checked with `playwright-cli`. Screenshots go to `.screenshots/`. The owner reviews them before the delivery's PR is marked ready.

## Review Focus

1. **A race edited after publication.** The trailer must show the confirmed published content, with images resolved through the published `carId`/`trackId`, never the race's current car or track. Covered in Task 9 and Task 10.
2. **A restore while a job runs.** The job stops, its partial output is deleted, and the previous trailer stays downloadable. Covered in Task 8 and Task 13.
3. **A matchday with only byes, or a playoff round without pairings.** The checklist reports "No races to show" and blocks the start, without crashing. Covered in Task 10.
4. **Very long names.** A 60-character team name, a track without a short name ("Michelin Raceway Road Atlanta") or a long car name must stay inside the 1920×1080 frame without overlapping other layers. Covered in Task 11.
5. **Music shorter than the video, or an MP3 with an ID3 tag.** Short music loops. An ID3-prefixed MP3 is accepted, and a renamed WAV or PNG is rejected. Covered in Task 3 and Task 12.

---

## Delivery 1: ffmpeg, short track names and music library (V48, schema 26)

### Task 1: ffmpeg in runtime image and CI, `MediaTool` wrapper

**Files:**
- Modify: `Dockerfile:29-30` (add `ffmpeg` to the runtime `apt-get install` line), `Dockerfile:47-50` (smoke step adds `ffmpeg -version && ffprobe -version`)
- Modify: `.github/actions/test-partition/action.yml` (install `ffmpeg` with apt before the test run, for all groups)
- Create: `src/main/java/org/ctc/media/MediaTool.java`, `src/main/java/org/ctc/media/MediaToolException.java`
- Test: `src/test/java/org/ctc/media/MediaToolTest.java`

**Interfaces:**
- Produces:
  - `@Component MediaTool` with `Duration probeDuration(Path audioOrVideo)` and `void run(List<String> ffmpegArgs, Duration timeout)`.
  - `MediaTool.probeStreams(Path video): ProbeResult` with `record ProbeResult(Duration duration, int width, int height, boolean hasAudio)`.
  - Executables come from `app.media.ffmpeg-path` (default `ffmpeg`) and `app.media.ffprobe-path` (default `ffprobe`).
  - Non-zero exit or timeout throws `MediaToolException` with the last 20 stderr lines, sanitized.

- [ ] **Step 1: Write failing tests**
  - `givenGeneratedSineMp3_whenProbeDuration_thenReturnsThreeSeconds`. The test creates the fixture with `MediaTool.run(List.of("-f","lavfi","-i","sine=d=3","-y", tmp.toString()), ofSeconds(30))`. Assert the duration is within 3 s ± 0.1 s.
  - `givenUnknownOption_whenRun_thenThrowsWithStderr`
  - `givenSlowCommand_whenTimeoutExceeded_thenProcessDestroyedAndThrows`
  - `givenTwoSecond1080pClip_whenProbeStreams_thenReportsSizeAndAudio`
- [ ] **Step 2:** `./scripts/test-feedback.py unit MediaToolTest`. Expected: FAIL to compile.
- [ ] **Step 3:** Implement `MediaTool`. Use `ProcessBuilder` with `-hide_banner -nostdin` prepended, and drain stderr on a bounded buffer. Call `ffprobe -v error -show_entries format=duration:stream=codec_type,width,height -of json` and parse the JSON with Jackson.
- [ ] **Step 4:** Run the same command. Expected: PASS.
- [ ] **Step 5:** Commit `feat(media): add ffmpeg to the runtime image and wrap ffmpeg/ffprobe calls`.

### Task 2: Optional track short name

**Files:**
- Create: `src/main/resources/db/migration/V48__track_short_name_and_music.sql`. It holds the `ALTER TABLE tracks ADD COLUMN display_short_name VARCHAR(30) NULL` part only; Task 3 appends the music table to the same file before delivery 1's PR.
- Modify:
  - `domain/model/Track.java` (`displayShortName`, length 30)
  - `admin/dto/TrackForm.java` (`@Size(max = 30) String displayShortName`)
  - `domain/service/TrackService.java:47`: the save signature gains `String displayShortName`, blank becomes null and the value is trimmed. Grep every caller of `save(` in `src/` and update it.
  - `admin/controller/TrackController.java:47,84`
  - `templates/admin/track-form.html`: a labelled input in `fieldset#track-identity` after country, with a hint that it is shown large in the trailer
  - `backup/restore/entity/TrackRestorer.java:38` (column list)
  - `backup/service/BackupImportService.java:140` (`SUPPORTED_SCHEMA_VERSIONS` adds 26)
  - `:776` `normalizeRestorableRow`: schemas below 26 get a null `displayShortName`, and a non-null value there is rejected with `DATA_MISMATCH`
  - `backup/schema/BackupSchema.java:35` → 26
- Create: `src/main/java/org/ctc/backup/schema/TrackShortNameArchive.java` with `static JsonNode normalize(int version, String table, JsonNode row)`
- Test: `TrackServiceTest`, `TrackShortNameArchiveTest`, `V48TrackShortNameMigrationIT` (H2) plus `V48TrackShortNameMigrationMariaDbIT`, and a round trip in the existing `BackupArchiveServiceIT`

**Interfaces:**
- Produces:
  - `Track.getDisplayShortName(): String` (nullable)
  - `TrackService.save(UUID id, String name, String country, String timeOfDayText, TrackRainCapability rain, String displayShortName)`

- [ ] **Step 1: Write failing tests**
  - `givenBlankShortName_whenSave_thenStoredAsNull`
  - `givenShortNameWithSpaces_whenSave_thenTrimmed`
  - `givenSchema25RowWithoutField_whenNormalize_thenNullShortName`
  - `givenSchema25RowWithShortName_whenNormalize_thenRejected`
  - `givenV48_whenInsert31Chars_thenRejected` (both databases)
  - Round trip: a track with `"SPA"` restores `"SPA"`.
- [ ] **Step 2:** `./scripts/test-feedback.py unit TrackServiceTest` and `./scripts/test-feedback.py unit TrackShortNameArchiveTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the files listed above.
- [ ] **Step 4:** Run the unit checks again, then `./scripts/test-feedback.py integration V48TrackShortNameMigrationIT --unit TrackServiceTest`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(tracks): add an optional display short name`.

### Task 3: Music library entity and management page

**Files:**
- Create:
  - `domain/model/MusicTrack.java`. It extends `BaseEntity` and has `title` (150), `artist` (150), `credit` (500, nullable), `filePath` (500), `durationSeconds` (int) and `createdAt`.
  - `domain/repository/MusicTrackRepository.java` (`findAllByOrderByTitleAscIdAsc`)
  - `domain/service/MusicLibraryService.java`
  - `admin/dto/MusicTrackForm.java` (`@NotBlank @Size(max=150) title`, `@NotBlank @Size(max=150) artist`, `@Size(max=500) credit`)
  - `admin/controller/MusicController.java` (`/admin/music`)
  - `templates/admin/music.html`
- Modify:
  - `V48__track_short_name_and_music.sql`: append `CREATE TABLE music_tracks`, with the columns above, `duration_seconds > 0` as a check, and `TIMESTAMP(6)` for `created_at`
  - `templates/admin/layout.html`: add a "Music" link in the "Tools" group, l.73-81
  - `admin/controller/GlobalModelAdvice.java`: `/admin/music` → `"music"`
- Test: `MusicLibraryServiceTest`, `MusicControllerIT`, `MusicLibraryE2ETest` (`@Tag("e2e")`)

**Interfaces:**
- Consumes: `MediaTool.probeDuration` (Task 1), `FileStorageService.storeBytes(String subDir, UUID id, String filename, byte[] data)`, `deleteOnRollback`, `deleteAfterCommit`.
- Produces:
  - `MusicLibraryService.list(): List<MusicTrack>`
  - `MusicTrack upload(MultipartFile file, String title, String artist, String credit)`
  - `void delete(UUID id)`
  - `Optional<MusicTrack> pick(UUID idOrNullForRandom, RandomGenerator random)`
  - `Path resolveFile(MusicTrack)`
  - Files are stored under subdir `music`.

- [ ] **Step 1: Write failing tests**
  - `givenId3TaggedMp3_whenUpload_thenStoredWithProbedDuration`
  - `givenFrameSyncMp3WithoutTag_whenUpload_thenAccepted`
  - `givenPngRenamedToMp3_whenUpload_thenRejectedWithMessage` (the message is "Only MP3 files are accepted.")
  - `givenFileOver20Mb_whenUpload_thenRejected`
  - `givenProbeFailure_whenUpload_thenStoredFileRemovedOnRollback`
  - `givenNullId_whenPick_thenRandomTrackFromLibrary`
  - `givenEmptyLibrary_whenPick_thenEmpty`
  - Controller IT: a POST without a title keeps the submitted artist and shows the field error. Delete removes the row and schedules the file deletion.
  - E2E: upload and delete without JavaScript, with keyboard focus on the labelled file input.
- [ ] **Step 2:** `./scripts/test-feedback.py unit MusicLibraryServiceTest`. Expected: FAIL.
- [ ] **Step 3:** Implement it.
  - Magic bytes: `ID3` at offset 0, or the first two bytes match `0xFF` and `(b & 0xE0) == 0xE0`.
  - The limit is 20 MB (`MAX_UPLOAD_BYTES = 20 * 1024 * 1024`).
  - Probe the stored file. A probe failure throws, so the rollback removes the file.
- [ ] **Step 4:** Run the unit test, then `./scripts/test-feedback.py integration MusicControllerIT --unit MusicLibraryServiceTest` and `./scripts/test-feedback.py browser MusicLibraryE2ETest --unit MusicLibraryServiceTest`. Expected: PASS.
- [ ] **Step 5:** Visual check of `/admin/music` with `playwright-cli`, desktop and mobile, saved to `.screenshots/music-*.png`.
- [ ] **Step 6:** Commit `feat(music): add a music library for trailers`.

### Task 4: Music backup, preflight and audit; delivery 1 docs and PR

**Files:**
- Create:
  - `backup/serialization/MusicTrackMixIn.java` (`@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})`)
  - `backup/restore/entity/MusicTrackRestorer.java`
  - `backup/schema/MusicTrackBackupValidator.java` with the constructor `(int version, Set<String> archivedUploads)`, plus `accept(String table, JsonNode row)` and `validate()`
  - `admin/service/MusicAuditService.java` with `inspect(): List<DataAuditFinding>`
- Modify:
  - `BackupSerializationModule.java:33`
  - `BackupExportService.java:275` (repository registration)
  - `:350` `enumerateReferencedUploads` adds every `filePath`
  - `BackupImportService.java:146-159`: `music_tracks` is absent for schemas below 26, following the `beforeImages` pattern
  - `:835-866`: create and feed the validator
  - `DataAuditService.java:94`
  - `DataAuditFinding` if a new category is needed
  - `BackupSchemaTopologyIT.java:32` → `hasSize(54)` with the updated message
  - `CLAUDE.md`, `README.md`
- Test: `MusicTrackBackupValidatorTest`, `MusicBackupRoundTripIT` (copy the structure of `CatalogImageBackupRoundTripIT`), `DataAuditServiceIT` (a synthetic missing file), `BackupImportSchemaMismatchIT` (schema 25 without the file is accepted)

**Interfaces:**
- Consumes: `MusicTrack`, `MusicTrackRepository` (Task 3).

- [ ] **Step 1: Write failing tests**
  - `givenSchema26WithoutMusicFile_whenValidate_thenRejectedAsMissingTable`
  - `givenRowWhoseFileIsNotArchived_whenValidate_thenRejected` (the message contains "is missing from the archive")
  - `givenTitleOf151Chars_whenValidate_thenRejected`
  - `givenZeroDuration_whenValidate_thenRejected`
  - Round trip: two tracks and their files restore byte-identical.
  - Audit: a missing file reports a finding with the track ID only.
- [ ] **Step 2:** `./scripts/test-feedback.py unit MusicTrackBackupValidatorTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the files listed above.
- [ ] **Step 4:** `./scripts/test-feedback.py integration MusicBackupRoundTripIT --unit MusicTrackBackupValidatorTest`, then the same with `DataAuditServiceIT` and `BackupSchemaTopologyIT`. Expected: PASS.
- [ ] **Step 5:** Update the docs:
  - `CLAUDE.md`: one paragraph on V48/schema 26, the music library and the short name
  - `README.md`: one line
  - the wiki page "Music library"
- [ ] **Step 6:** Commit `feat(backup): export the music library and track short names`. Rebase onto `origin/master`, push, open the PR, check the CI with `gh pr checks`. Merge only after green CI and owner approval.

## Delivery 2: car cutouts (V49, schema 27)

### Task 5: `CUTOUT` kind and `AUTO` source

**Files:**
- Create: `src/main/resources/db/migration/V49__car_image_cutout.sql`. It drops and re-adds `chk_car_image_kind` with `CUTOUT`, and `chk_car_image_source` with an `AUTO` branch that requires `source_key = 'cutout'`. The pattern is V47.
- Modify:
  - `CarImageKind.java`: add `CUTOUT`
  - `ImageSource.java`: add `AUTO("Automatic", null)`, and replace the key rule `isDownloaded()` with `requiresSourceKey()` (`GT7`, `GTENGINE`, `AUTO`). Grep every `isDownloaded()` caller and keep the meaning "downloaded from a remote" where callers mean that.
  - `CatalogImageBackupValidator.java`: `CUTOUT_VERSION = 27`. Below 27, reject `CUTOUT`/`AUTO`. From 27 on, `AUTO` requires the key `cutout` and the kind `CUTOUT`.
  - `CatalogImageAuditService.java`: same rules, read-only.
  - `BackupSchema.java:35` → 27
  - `BackupImportService.java:140` adds 27
- Test: `CatalogImageBackupValidatorTest`, `V49CarImageCutoutMigrationIT` plus `V49CarImageCutoutMigrationMariaDbIT`, `DataAuditServiceIT`

**Interfaces:**
- Produces: `CarImageKind.CUTOUT`, `ImageSource.AUTO`, `ImageSource.requiresSourceKey(): boolean`, and the constant `CatalogImageService.AUTO_CUTOUT_KEY = "cutout"`.

- [ ] **Step 1: Write failing tests**
  - `givenV49_whenInsertSecondAutoCutoutForCar_thenUniqueViolation`
  - `givenV49_whenInsertAutoWithOtherKey_thenCheckViolation`
  - `givenSchema26ArchiveWithCutout_whenValidate_thenRejected`
  - `givenSchema27AutoCutout_whenValidate_thenAccepted`
  - `givenAutoStudioRow_whenAudit_thenFinding`
- [ ] **Step 2:** `./scripts/test-feedback.py unit CatalogImageBackupValidatorTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the files listed above.
- [ ] **Step 4:** `./scripts/test-feedback.py integration V49CarImageCutoutMigrationIT --unit CatalogImageBackupValidatorTest`, plus the MariaDB IT with `--docker`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(images): add automatic car cutouts to the gallery model`.

### Task 6: Container memory 3 GB

**Files:**
- Modify:
  - `deploy/compose.yaml:97` → `mem_limit: 3g`
  - `:104` → `-XX:MaxRAMPercentage=33.0`
  - `deploy/lib/probe.py:85` → `'3g'`
  - `deploy/lib/recovery.py:473` (same options)
  - `deploy/lib/host.py:169` → `5 * 1024**3`, with the message naming 5 GiB
  - `deploy/tests/test_recipe.py:295` → `3221225472`
  - the host setup guide, wherever it states 4 GiB
- Test: `bash deploy/tests/recovery.sh`, `bash deploy/tests/rollout.sh`, `./scripts/test-feedback.py unit DeploymentScriptsTest`

- [ ] **Step 1:** Change the test expectations first: `test_recipe.py` and the host minimum test. Run `bash deploy/tests/acceptance.sh`. Expected: FAIL on the old values.
- [ ] **Step 2:** Change the deploy files.
- [ ] **Step 3:** Run `bash deploy/tests/acceptance.sh`, then `./scripts/test-feedback.py unit DeploymentScriptsTest`. Expected: PASS.
- [ ] **Step 4:** Commit `feat(deploy): give the app container 3 GB for media rendering`.

### Task 7: Segmentation model in image, CI and dev

**Files:**
- Modify:
  - `pom.xml`: add `com.microsoft.onnxruntime:onnxruntime` with a version property that Renovate can see
  - `Dockerfile`: a stage `cutout-model` downloads the pinned model with `curl -fsSL` and checks `sha256sum -c`. The runtime stage copies it to `/opt/ctc/models/cutout.onnx`, readable by `ctc`. The smoke step checks that the file exists.
  - `.github/actions/test-partition/action.yml`: `actions/cache` keyed by the SHA-256, fetch on a miss, and export `APP_TRAILER_CUTOUT_MODEL_PATH`
  - `application.yml`: `app.trailer.cutout-model-path` (default `data/models/cutout.onnx`); `docker`/`prod` use `/opt/ctc/models/cutout.onnx`
- Create:
  - `scripts/fetch-cutout-model.sh`, which reads the URL and SHA-256 from `config/cutout-model.properties` (the single source for Dockerfile, CI and dev)
  - `config/cutout-model.properties`
  - `src/main/java/org/ctc/media/cutout/CutoutModel.java`
  - `src/main/java/org/ctc/media/cutout/CutoutModelUnavailableException.java`
- Test: `CutoutModelTest`, with the fixture `src/test/resources/cutout/studio-fixture.jpg`. This is a 640×360 crop of a GT7 studio shot already stored in the repo's test resources, or a synthetic car-shaped image if none exists.

**Interfaces:**
- Produces:
  - `CutoutModel.isAvailable(): boolean`
  - `BufferedImage cutout(BufferedImage studio)`: returns ARGB with the predicted alpha, trimmed to the alpha bounding box plus a 24 px margin. It throws `CutoutModelUnavailableException("Cutout model not installed")` when the file is missing.
  - `void release()`: closes the `OrtSession`.

- [ ] **Step 1: Choose the model.**
  - Run BiRefNet-lite and ISNet general-use on the seven CLR studio shots in `~/Desktop/CLR-100km/_source/cars`, with a throwaway script under the scratchpad.
  - Compare the results with `~/Desktop/CLR-100km/_source/cutouts` and record peak RSS for each.
  - Put the comparison sheet in `.screenshots/cutout-compare.png`. The owner picks the model.
  - Record URL, SHA-256, input size and normalisation in `config/cutout-model.properties`.
- [ ] **Step 2: Write failing tests**
  - `givenStudioFixture_whenCutout_thenCornersTransparentAndCentreOpaque`: alpha is 0 at the four corners and above 200 at the centre of the car.
  - `givenStudioFixture_whenCutout_thenTrimmedToBoundingBoxWithMargin`
  - `givenMissingModelFile_whenCutout_thenThrowsUnavailable`
  - `givenMissingModelFile_whenIsAvailable_thenFalse`
- [ ] **Step 3:** `./scripts/test-feedback.py unit CutoutModelTest`. Expected: FAIL.
- [ ] **Step 4:** Implement it. Create the session lazily, resize to the model's input size, normalise as recorded, upscale the mask bilinearly to the source size, and use it as alpha.
- [ ] **Step 5:** Run again. Expected: PASS. Check `docker build .` locally once, so the stage and the checksum work.
- [ ] **Step 6:** Commit `feat(media): package the car cutout model`.

### Task 8: Single job executor and cutout service

**Files:**
- Create:
  - `src/main/java/org/ctc/media/job/MediaJobExecutor.java`
  - `MediaJob.java`
  - `MediaJobStatus.java` (`record MediaJobStatus(UUID jobId, MediaJobKind kind, String scopeKey, MediaJobState state, int step, int steps, String message, Instant updatedAt)`)
  - `MediaJobKind` (`CUTOUT`, `TRAILER`)
  - `MediaJobState` (`QUEUED`, `RUNNING`, `DONE`, `FAILED`, `ABANDONED`)
  - `src/main/java/org/ctc/domain/service/CarCutoutService.java`
- Modify:
  - `CatalogImageService.java`: add `applyAutoCutout(UUID carId, String imagePath, int w, int h): boolean`, modelled on `applyGtEngineMap:140` (replace the existing AUTO, delete the old file after commit, delete the new file on rollback)
  - `CatalogImageController.java`: `POST /admin/cars/{id}/images/cutout` submits a job and redirects with a status
  - `catalog-gallery.html` l.40-44: a "Create cutout" form when `galleryCutout` is true, plus a status line for a running job of this car
  - `CarController.java:117-122`: model attributes `galleryCutout` and `galleryCutoutStatus`
- Test: `MediaJobExecutorTest`, `CarCutoutServiceIT`, `CatalogImageControllerIT` (extend it)

**Interfaces:**
- Consumes: `CutoutModel` (Task 7), `CatalogImageService.firstCarImage(UUID, CarImageKind)`, `ImportLockService.tryEnterWriter()`/`exitWriter()`, `PickSessionEpoch.getAsLong()`, `BackupImportSucceededEvent`.
- Produces:
  - `MediaJobExecutor.submit(MediaJobKind kind, String scopeKey, MediaJob job): MediaJobStatus`. It throws `MediaJobBusyException` while another job is not finished.
  - `Optional<MediaJobStatus> status(String scopeKey)`
  - `MediaJob` is a functional interface `void run(MediaJobContext ctx) throws Exception`.
  - `MediaJobContext.progress(int step, int steps, String message)` and `MediaJobContext.checkNotAbandoned()`. The latter throws `MediaJobAbandonedException` when the restore generation changed or a restore event arrived.
  - `CarCutoutService.createCutout(UUID carId): boolean`: false when the car has no `STUDIO` image. Its write runs under `tryEnterWriter` in a fresh transaction after the inference.
  - Scope keys are `car:<uuid>`, `matchday:<uuid>` and `round:<uuid>`.

- [ ] **Step 1: Write failing tests**
  - `givenRunningJob_whenSubmitSecond_thenBusy`
  - `givenRestoreEventDuringJob_whenCheckNotAbandoned_thenAbandonedState`
  - `givenJobThrows_whenFinished_thenFailedWithSanitizedMessage`
  - `givenFinishedJob_whenSubmit_thenAccepted`
  - `givenImportLockHeld_whenSubmit_thenRejected("A restore is running.")`
  - IT: `givenCarWithStudio_whenCreateCutout_thenOneAutoCutoutStored`
  - IT: `givenExistingAutoCutout_whenCreateCutoutAgain_thenReplacedAndOldFileDeletedAfterCommit`
  - IT: `givenManualCutoutFirst_whenCreateCutout_thenManualKeepsSortPosition`
  - IT: `givenRestoreLockHeld_whenCreateCutout_thenRejectedWithoutWrite`
  - Controller IT: a POST while busy shows the error "Another media job is running."
- [ ] **Step 2:** `./scripts/test-feedback.py unit MediaJobExecutorTest`. Expected: FAIL.
- [ ] **Step 3:** Implement it.
  - Use a single-thread `ExecutorService` owned by the bean and shut down on `@PreDestroy`.
  - Keep the state in an `AtomicReference` per scope key, plus the last finished status.
  - Call `CutoutModel.release()` in the job's `finally` block.
- [ ] **Step 4:** `./scripts/test-feedback.py integration CarCutoutServiceIT --unit MediaJobExecutorTest`. Expected: PASS.
- [ ] **Step 5:** Visual check of the car gallery with the button and a finished cutout, saved to `.screenshots/car-cutout-*.png`.
- [ ] **Step 6:** Update `CLAUDE.md` (V49/schema 27, cutouts, 3 GB), `README.md` and the wiki "Car images" page. Commit `feat(images): create car cutouts automatically`. Rebase, then open the PR for delivery 2.

## Delivery 3: scene plan and scene rendering (PNG ZIP)

### Task 9: Published content with IDs

**Files:**
- Modify: `domain/service/PickPublicationService.java`. Add `Optional<PickPublicationContent> releasedContent(Race race)`, next to `settingsReleased(Race):222`.
- Test: `PickPublicationServiceTest` (extend it)

**Interfaces:**
- Produces: `PickPublicationService.releasedContent(Race)`.
  - Present when the race is finished: `PickPublicationContent.from(race)`.
  - Present when the latest confirmed content has every `PickDisclosureField` released.
  - Otherwise empty.

- [ ] **Step 1: Write failing tests**
  - `givenConfirmedFullRelease_whenReleasedContent_thenContentWithCarAndTrackIds`
  - `givenOnlyCarAndTrackReleased_whenReleasedContent_thenEmpty`
  - `givenUnpublishedLaterEdit_whenReleasedContent_thenReturnsConfirmedVersionNotCurrentRace`: the race's car changes after the confirmation, and the returned `carId` is the published one.
  - `givenNoAttempt_whenReleasedContent_thenEmpty`
- [ ] **Step 2:** `./scripts/test-feedback.py unit PickPublicationServiceTest`. Expected: FAIL.
- [ ] **Step 3:** Implement it, reusing the confirmation lookup from `publicProjection:203-217` through a private helper shared by both.
- [ ] **Step 4:** Run again. Expected: PASS.
- [ ] **Step 5:** Commit `feat(picks): expose fully released publication content`.

### Task 10: Trailer plan and checklist

**Files:**
- Create, under `src/main/java/org/ctc/trailer/`:
  - `TrailerScope.java`: a sealed interface with `MatchdayScope(UUID matchdayId)` and `PlayoffRoundScope(UUID roundId)`, plus `String key()`
  - `TrailerPlanService.java`
  - `TrailerPlan.java`
  - `TrailerRaceScene.java`
  - `TrailerChecklistItem.java`
- Test: `TrailerPlanServiceTest`, `TrailerPlanServiceIT`

**Interfaces:**
- Consumes:
  - `AbstractMatchdayGraphicService.prepareBaseContext(Matchday): MatchdayGraphicData`
  - `AbstractPlayoffRoundGraphicService.prepareBaseContext(PlayoffRound): MatchdayGraphicData`
  - `PickPublicationService.releasedContent(Race)` (Task 9)
  - `CatalogImageService.firstCarImage`/`firstTrackImage`
  - `MusicLibraryService.list()`
  - `CutoutModel.isAvailable()`
- Produces:
  - `record TrailerPlan(TrailerScope scope, String title, String seasonLabel, MatchdayGraphicData overview, List<TrailerRaceScene> races, List<TrailerChecklistItem> blockers, Set<UUID> carsNeedingCutout)`
  - `record TrailerRaceScene(UUID raceId, String pairingLabel, String homeTeam, String awayTeam, String homeLogo, String awayLogo, List<Integer> legNumbers, PickPublicationContent content, UUID trackId, String trackName, String trackShortName, UUID carId, String carName)`
  - `record TrailerChecklistItem(String message, String fixUrl)`
  - `TrailerPlanService.plan(TrailerScope scope): TrailerPlan`, with `@Transactional(readOnly = true)`
  - `Duration estimatedLength(TrailerPlan)`, built from the constants `INTRO = 4s`, `OVERVIEW = 8s`, `RACE_VISUAL = 6s`, `RACE_SETTINGS = 8s`, `OUTRO = 5s`

- [ ] **Step 1: Write failing tests (unit, mocked repositories)**
  - `givenTwoLegsWithEqualContentFingerprint_whenPlan_thenOneSceneWithLegs1And2`
  - `givenTwoLegsWithDifferentTrack_whenPlan_thenTwoScenes`
  - `givenByeMatch_whenPlan_thenExcluded`
  - `givenOnlyByes_whenPlan_thenBlocker("No races to show")`
  - `givenUnreleasedRace_whenPlan_thenBlocker("Setup of <pairing> leg <n> is not published")` with a link to the race
  - `givenTrackWithoutBackground_whenPlan_thenBlocker` with the link `/admin/tracks/<id>/edit#track-images`. The same applies to the layout and the logo.
  - `givenCarWithoutStudioOrCutout_whenPlan_thenBlocker`
  - `givenCarWithStudioOnly_whenPlan_thenInCarsNeedingCutoutAndNoBlocker`
  - `givenCarsNeedingCutoutAndModelMissing_whenPlan_thenBlocker("Cutout model not installed")`
  - `givenEmptyMusicLibrary_whenPlan_thenBlocker` with the link `/admin/music`
  - `givenSixMatchesIdenticalLegs_whenEstimatedLength_then1m41s`
- [ ] **Step 2:** `./scripts/test-feedback.py unit TrailerPlanServiceTest`. Expected: FAIL.
- [ ] **Step 3:** Implement it.
  - Dedupe consecutive legs of one pairing by `PickPublicationContent.fingerprint()`.
  - Order the scenes like the overview rows.
  - Resolve the track and car through `content.trackId()` and `content.carId()` only.
- [ ] **Step 4:** Run the unit test. Then write the IT `givenTestSeasonMatchdayWithPublishedPicks_whenPlan_thenNoBlockers` on `TestDataService` fixtures with a `Test_` prefix, and run `./scripts/test-feedback.py integration TrailerPlanServiceIT --unit TrailerPlanServiceTest`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(trailer): plan trailer scenes and preconditions`.

### Task 11: Fonts, scene templates and layer rendering

**Files:**
- Create:
  - `src/main/resources/static/admin/fonts/BarlowCondensed-{Medium,SemiBold,Bold}.woff2`, `Barlow-{Medium,SemiBold}.woff2`, `OFL-Barlow.txt`
  - `src/main/resources/templates/admin/trailer/{intro,overview,race-visual,race-settings,outro}.html`
  - `src/main/resources/templates/admin/trailer/fragments.html` (shared `@font-face`, `:root` tokens, brand block, watermark)
  - `src/main/java/org/ctc/trailer/TrailerSceneRenderer.java`
  - `src/main/java/org/ctc/trailer/TrailerLayer.java`
- Test: `TrailerSceneRendererIT` (`@Tag("integration")`, it uses Chromium)

**Interfaces:**
- Consumes:
  - `TrailerPlan` (Task 10)
  - `SandboxedHtmlRenderer.screenshot(String html, int w, int h, boolean omitBackground, Path out)`
  - `AbstractGraphicService.encodeClasspathResource(String, String)`
  - The image data URI must use the real MIME type: JPEG, PNG or WebP, detected from the magic bytes with `ImageContent`.
  - The CTC logo and bolt come from `static/admin/img/ctc-logo-white.png`.
- Produces:
  - `record TrailerLayer(String scene, int sceneIndex, String name, Path png, Duration sceneLength)`
  - `TrailerSceneRenderer.render(TrailerPlan plan, Path workDir, MediaJobContext ctx): List<TrailerLayer>`. Layer names per scene: `background`, `car` (race-visual only) and `foreground`. Non-background layers are transparent PNGs.
  - `Path zipLayers(List<TrailerLayer>, Path zipFile)`

Design tokens, copied from the spec: `#0b0b0b` background, `#161616` cards, `#ffd400` accent, `#8a8a8a`/`#5c5c5c` greys, label letter spacing 6-22 px, watermark opacity 0.07. Conthrax for brand and headlines, Barlow Condensed for labels and figures, Barlow for body text. Use the recovered teaser HTML as a reference: `/private/tmp/claude-501/-Users-jegr-Documents-github-ctc-manager/d42f0917-05e1-4458-a515-efb1488f1c40/scratchpad/teaser-recovered.html`. If it is gone, use `~/Downloads/ctc-2027-teaser.png`.

What each scene shows:
- **race-visual:** large `trackShortName` with the full name below, or only the full name in a smaller size; the track logo; the layout map; the car name; the pairing label.
- **race-settings:** tiles for the settings plus both team logos and names.
- **overview:** a pairing list with logos, seeds and records, and no times.
- **intro and outro:** brand block, season and title. The outro adds the static text "Join us on Discord".

- [ ] **Step 1: Write failing IT tests**
  - `givenPlanWithTwoScenes_whenRender_thenLayerFilesExistWith1920x1080`
  - `givenRaceVisual_whenRender_thenCarLayerHasTransparentCorners`
  - `givenLongNames_whenRender_thenTextFitsFrame`. Use 60-character team names, no short name and a 45-character car name. Render a debug variant whose template sets `data-overflow` on any element with `scrollWidth > clientWidth`. Assert that the debug screenshot has no red overflow outline pixels. The renderer supplies the outline style only when `debugOverflow=true`.
  - `givenLayers_whenZip_thenEntriesNamedSceneIndexAndLayer`
- [ ] **Step 2:** `./scripts/test-feedback.py integration TrailerSceneRendererIT --unit TrailerPlanServiceTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the templates and the renderer. Text fits by fixed max widths with `overflow-wrap` and two-step font-size classes chosen in Java by character count. Templates contain no logic.
- [ ] **Step 4:** Run again. Expected: PASS.
- [ ] **Step 5: Visual checkpoint.** Render every scene for a dev-profile matchday into `.screenshots/trailer-<scene>.png`. Name the paths to the owner and wait for free-text feedback. Iterate in small commits.
- [ ] **Step 6:** Update `CLAUDE.md`, `README.md` and the wiki. Commit `feat(trailer): render trailer scenes as layers`. Rebase, then open the PR for delivery 3.

## Delivery 4: video, job, UI

### Task 12: Video composition

**Files:**
- Create: `src/main/java/org/ctc/trailer/TrailerVideoComposer.java`, `src/main/java/org/ctc/trailer/FfmpegGraphBuilder.java`
- Test: `FfmpegGraphBuilderTest`, `TrailerVideoComposerIT`

**Interfaces:**
- Consumes: `TrailerLayer` (Task 11), `MediaTool.run`/`probeStreams` (Task 1), `MusicTrack` + `MusicLibraryService.resolveFile` (Task 3).
- Produces:
  - `FfmpegGraphBuilder.build(List<TrailerLayer> layers, Path music, Duration musicLength, Path out): List<String>`. This is a pure function returning the full argument list.
  - `TrailerVideoComposer.compose(List<TrailerLayer>, MusicTrack, Path out, MediaJobContext ctx): Path`

Fixed values:
- 30 fps, 1920×1080, `libx264 -preset medium -crf 20 -pix_fmt yuv420p`, AAC 192k, `-movflags +faststart`
- 0.5 s cross-fade between scenes
- background zoom from 1.00 to 1.06 over the scene
- foreground slides 80 px from the left with a fade over 0.4 s; the car slides from the right
- the audio loops with `-stream_loop -1` when shorter, is cut to the video length and fades out over the last 2 s

- [ ] **Step 1: Write failing tests**
  - `givenTwoScenes_whenBuild_thenArgsContainXfadeAtSceneBoundaryMinusHalfSecond`
  - `givenMusicShorterThanVideo_whenBuild_thenStreamLoopPresent`
  - `givenMusicLongerThanVideo_whenBuild_thenNoStreamLoopAndAfadeOutAtLengthMinus2`
  - `whenBuild_thenNoShellMetacharactersAndAllPathsAbsolute`
  - IT: `givenRenderedFixtureLayers_whenCompose_thenMp4WithExpectedDurationResolutionAndAudio`. Check with `probeStreams`: duration equals the sum of scene lengths minus the cross-fades, ± 0.1 s; size 1920×1080; `hasAudio`.
- [ ] **Step 2:** `./scripts/test-feedback.py unit FfmpegGraphBuilderTest`. Expected: FAIL.
- [ ] **Step 3:** Implement it.
- [ ] **Step 4:** `./scripts/test-feedback.py integration TrailerVideoComposerIT --unit FfmpegGraphBuilderTest`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(trailer): compose trailer video with ffmpeg`.

### Task 13: Trailer job and outputs

**Files:**
- Create:
  - `src/main/java/org/ctc/trailer/TrailerService.java`
  - `TrailerOutputs.java` (`record TrailerOutputs(Path mp4, Path zip, Path youtubeText, Instant createdAt, String musicTitle)`)
  - `YoutubeTextBuilder.java`
- Test: `YoutubeTextBuilderTest`, `TrailerServiceIT`

**Interfaces:**
- Consumes: `TrailerPlanService.plan` (Task 10), `CarCutoutService.createCutout` (Task 8), `TrailerSceneRenderer` (Task 11), `TrailerVideoComposer` (Task 12), `MediaJobExecutor.submit` (Task 8), `MusicLibraryService.pick` (Task 3).
- Produces:
  - `TrailerService.start(TrailerScope scope, UUID musicIdOrNull): MediaJobStatus`. It throws `TrailerBlockedException(List<TrailerChecklistItem>)` when blockers exist.
  - `Optional<TrailerOutputs> latest(TrailerScope)`
  - `Optional<MediaJobStatus> status(TrailerScope)`
  - Outputs go to `<upload-dir>/trailers/<matchday|round>/<uuid>/`. Work happens in a sibling `.work-<jobId>` directory, which replaces the previous files only after success.

Job steps:
1. Create the cutouts for `carsNeedingCutout`, each committed on its own.
2. Re-plan, to pick up the new cutouts, and fail if any blockers remain.
3. Render the layers.
4. Write the ZIP.
5. Compose the MP4.
6. Write `youtube.txt`.
7. Swap the directory.

Call `ctx.checkNotAbandoned()` between steps and between scenes. On failure or abandonment, delete the work directory.

- [ ] **Step 1: Write failing tests**
  - `givenMatchdayPlan_whenBuildYoutubeText_thenTitleContainsSeasonAndMatchdayAndDescriptionEndsWithMusicCredit`. Without a credit, the fallback is `"Music: <title> by <artist>"`.
  - IT: `givenReadyMatchday_whenStartAndAwait_thenOutputsExistAndLatestReturnsThem`
  - IT: `givenPreviousTrailer_whenNewJobFails_thenPreviousOutputsKept`
  - IT: `givenRestoreEventDuringRender_whenJobEnds_thenAbandonedAndWorkDirDeletedAndPreviousKept`
  - IT: `givenBlockers_whenStart_thenTrailerBlockedException`
  - IT: `givenReadyMatchday_whenStartAndAwait_thenPeakMemoryRecorded`. It writes the process peak RSS (`VmHWM` from `/proc/self/status` on Linux, `ProcessHandle` fallback elsewhere) to `target/test-perf/trailer-memory.txt`, which CI uploads with the other test-perf files. There is no threshold assertion; the PR body states the measured value against the 3 GB limit.
- [ ] **Step 2:** `./scripts/test-feedback.py unit YoutubeTextBuilderTest`. Expected: FAIL.
- [ ] **Step 3:** Implement it.
- [ ] **Step 4:** `./scripts/test-feedback.py integration TrailerServiceIT --unit YoutubeTextBuilderTest`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(trailer): run trailer rendering as a background job`.

### Task 14: Trailer section in management

**Files:**
- Create:
  - `admin/controller/TrailerController.java` with these routes:
    - `POST /admin/matchdays/{id}/trailer` and `POST /admin/playoffs/round/{roundId}/trailer` take the form field `musicId` (blank means random)
    - `GET .../trailer/status` returns a fragment
    - `GET .../trailer/{file}` with `file` in `video`, `layers` or `youtube` streams the file as an attachment with a fixed filename
  - `admin/dto/TrailerStartForm.java`
  - `templates/admin/fragments/trailer-section.html`
  - `static/admin/js/trailer-status.js` (polls every 3 s while running; without JavaScript, the page shows the status on reload)
- Modify:
  - `templates/admin/matchday-detail.html`: include after `div#matchday-graphics` (l.151-177)
  - `templates/admin/playoff-bracket.html` l.69-79: per round, a details disclosure that holds the fragment
  - `MatchdayController.detail` (l.61) and `PlayoffController.detail` (l.54): add model attributes through a `TrailerViewService.view(TrailerScope): TrailerView`, so the controllers stay thin
- Test: `TrailerControllerIT`, `MatchdayTrailerE2ETest` (`@Tag("e2e")`)

**Interfaces:**
- Consumes: `TrailerService` (Task 13), `TrailerPlanService.plan`/`estimatedLength` (Task 10), `MusicLibraryService.list` (Task 3).
- Produces: `record TrailerView(List<TrailerChecklistItem> blockers, List<MusicTrack> music, Optional<MediaJobStatus> status, Optional<TrailerOutputs> latest, Duration estimatedLength, boolean startEnabled)`.

- [ ] **Step 1: Write failing tests**
  - Controller IT: `givenBlockers_whenGetMatchday_thenChecklistLinksAndStartDisabled`
  - `givenReady_whenPostTrailer_thenRedirectWithStatusRunning`
  - `givenBusy_whenPostTrailer_thenErrorMessage`
  - `givenOutputs_whenDownloadVideo_thenVideoMp4AttachmentNamedCtcTrailerSeasonMatchday`
  - `givenUnknownFile_whenDownload_then404`
  - E2E: with JavaScript disabled, the start button is a native submit, and a reload shows the progress text. With JavaScript, the status updates without a reload and announces through `aria-live="polite"`.
- [ ] **Step 2:** `./scripts/test-feedback.py integration TrailerControllerIT --unit YoutubeTextBuilderTest`. Expected: FAIL.
- [ ] **Step 3:** Implement it.
- [ ] **Step 4:** Run the IT again, then `./scripts/test-feedback.py browser MatchdayTrailerE2ETest --unit YoutubeTextBuilderTest`. Expected: PASS.
- [ ] **Step 5: Visual checkpoint.** Desktop and mobile screenshots of both pages go to `.screenshots/trailer-section-*.png`. Render one full trailer in dev for the owner to watch. Wait for feedback.
- [ ] **Step 6:** Update `CLAUDE.md` (trailer job rules), `README.md` and the wiki page "Matchday trailer". Commit `feat(trailer): add trailer controls to matchdays and playoff rounds`. Rebase, then open the PR for delivery 4. Merge after green CI and owner approval.
