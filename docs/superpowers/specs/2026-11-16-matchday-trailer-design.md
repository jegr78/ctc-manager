# Matchday trailer

Management creates a short YouTube trailer for each matchday or playoff round. Until now this was a hand-built Canva video ("CTC - 2026 - KO2 - Trailer", 2:30): intro, pairing overview, then for every match a settings card, a track shot and a car shot, and an outro. The same steps repeat every matchday, so CTC Manager renders the trailer from its own data with one action.

## Goals

- One action per matchday or playoff round renders a finished MP4 that can be uploaded to YouTube without editing.
- The trailer uses the new CTC look of the 2027 teaser.
- Car cutouts are produced automatically on the production Linux box.
- Missing data blocks the render with a checklist instead of producing a trailer with gaps.

## Non-goals

- YouTube upload or any other automatic publication.
- Race dates, start times, stream channels or links. When the trailer is made, these are usually unknown.
- Portrait formats (Shorts, Instagram).
- Changes to the existing Discord settings graphic or other existing graphics.
- Configurable scene durations or a free scene editor.

## Anti-goals

- A trailer that shows setup data before `PickPublicationService.publicProjection` releases it.
- Silent fallbacks for missing images or setup data.
- A second runtime service or a network download at render time.

## Timing

Management renders the trailer once every pick of the matchday is published. Race dates may still be open at that point.

## Output

- MP4, H.264 + AAC, 1920×1080, 30 fps.
- ZIP with every rendered scene layer as PNG, for manual re-editing.
- A text file with a suggested YouTube title and description. The description contains the music credit.

All three are downloads in management. Each matchday or playoff round keeps only its latest trailer; a new render replaces it. Files live under `uploads/trailers/<scope>/<id>/`. They are not referenced by any entity and are not part of backups, since they can be rendered again.

## Scenes

| Scene | Duration | Content |
|---|---|---|
| Intro | 4 s | CTC brand block, season, matchday or playoff round name |
| Overview | 8 s | All non-bye pairings with team logos, seeds and records; no times |
| Track + car | 6 s per race | Track `BACKGROUND` full-bleed and darkened, track logo, track `LAYOUT` map, track short name, studio car cutout, car name, both team logos with seed and record |
| Car in action | 4 s per race | First car `SCENE` image full-bleed without cutout, car name, leg, pairing and team logos |
| Settings | 8 s per race | Settings tiles in the new look (laps, pit stops, tyre and fuel wear, refuel speed, initial fuel, time of day, progression, slipstream, mandatory tyres, start type, available tyres, weather), both teams with seed and record |
| Outro | 5 s | CTC logo, matchday or round name, the slogan "GAME ON!" |

The length follows the number of races: with six matches and identical legs, the trailer runs about 2:05. Track logos keep a transparent background; dark logos have their grey pixels inverted so they read on the dark scenes while coloured parts keep their colour.

Scenes are generated per race. When both legs of a pairing have identical public setup content (car, track and every published setting), they share one track + car scene and one settings scene.

Car and track images are looked up through the `carId` and `trackId` of the published `PickPublicationContent`, never through `race.getCar()` or `race.getTrack()`, which may hold a later unpublished change. `publicProjection` carries names only, so `PickPublicationService` gains a method that returns the published content of a race only when every field is released.

Regular matchdays (league, groups and Swiss) and playoff rounds are both supported from the start. The overview reuses the data that `MatchdayOverviewGraphicService` and the playoff round graphic services already prepare, without the schedule column.

Durations are constants in code.

## Look

The trailer follows the 2027 teaser (`ctc-2027-teaser.png`), which a previous session rendered from HTML:

- Fonts:
  - Conthrax for brand and headlines
  - Barlow Condensed 500/600/700 for labels and figures
  - Barlow 500/600 for body text
- Colours: background `#0b0b0b`, cards `#161616`, accent yellow `#ffd400`, greys `#8a8a8a` and `#5c5c5c`
- Labels in wide letter spacing (6 to 22 px)
- The CTC bolt as a watermark at about 7 % opacity
- The brand block on black

The scene layouts take their composition from the CLR 100 km graphics in `~/Desktop/CLR-100km`: a large short track name with a smaller subtitle, the cutout car overlapping a darkened track image, and settings as labelled tiles. Colours and fonts stay CTC's.

Barlow and Barlow Condensed (SIL OFL) are added as WOFF2 files under `static/admin/fonts`. The sandboxed renderer is offline, so templates inline them as data URIs like Conthrax.

## Rendering

1. Playwright renders each scene as separate 1920×1080 PNG layers through the existing `SandboxedHtmlRenderer`. Typical layers are the background, the car cutout, text blocks and transparent overlays. Templates are Thymeleaf files under `templates/admin/trailer/`, with images and fonts inlined.
2. ffmpeg composes the layers into motion:
   - slow zoom on background images
   - slide-in and fade of foreground layers
   - cross-fades between scenes
3. ffmpeg mixes the music in. A shorter track loops and a longer one is cut. The audio fades out over the last 2 seconds.

Motion uses only ffmpeg filters. There is no frame-by-frame HTML rendering, so the sandbox keeps JavaScript disabled.

ffmpeg is installed in the runtime Docker image from the pinned Ubuntu Noble packages. The Dockerfile keeps the existing Noble pins, non-root user and browser cache contract. The app calls ffmpeg as a child process with a fixed argument list and no shell. Input paths are application-generated files only.

Custom-template overrides through `TemplateManageable` are not offered for trailer scenes in this scope.

## Background job

- A single in-process executor runs at most one job at a time, either a trailer render or a gallery cutout. Further start requests are rejected with a message. This keeps at most one model instance, one Chromium and one ffmpeg process alive.
- A trailer job runs its steps strictly in sequence: cutouts, then scene rendering, then encoding.
- Each automatic cutout commits on its own, under restore writer admission and a fresh read of the car, before the read-only scene capture. Cutouts stay when the trailer later fails.
- Job state stays in memory: queued, rendering scene n of m, encoding, done, failed. The management page polls a small status endpoint, and a native reload shows the same state without JavaScript.
- The job captures all scene data in one read-only transaction at start. Rendering and encoding run outside any transaction.
- A committed restore or an application restart abandons a running job and deletes its partial output. Work happens outside the upload tree. Since trailers are not in backups, a restore leaves no trailer files behind; they are rendered again.
- The job respects `ImportLockService`: it does not start while a restore holds the lock.
- Failures keep the previous trailer and show the ffmpeg or renderer error in sanitized form.

## Preconditions checklist

Before start, a service builds a checklist for the matchday or round. Every open item blocks the start and links to the place where it is fixed:

- every non-bye race has a public setup, according to `PickPublicationService.publicProjection`, with car, track and all settings shown in the settings scene
- every track used has a logo (`Track.imageUrl`), a `BACKGROUND` image and a `LAYOUT` image
- every car used has a `STUDIO` image or a `CUTOUT` image, and a `SCENE` image
- at least one music track exists

Missing cutouts are not blocking. The job creates them (see below).

## Car cutouts

The gallery spec (`2026-11-15-car-track-image-gallery-design.md`) is extended:

- Car image kind `CUTOUT`: a PNG with alpha channel.
- Image source `AUTO` for cutouts the application produced. Its `sourceKey` is the fixed value `cutout`, so the existing unique constraint on car, source and key allows at most one `AUTO` cutout per car.

Cutouts are produced with ONNX Runtime for Java on the CPU and a salient-object segmentation model (BiRefNet-lite, MIT, or ISNet general-use, Apache-2.0). The owner chose ISNet general-use on 2026-11-16 after a comparison on the seven CLR studio shots against the Apple Vision cutouts. Quality was close; ISNet needed about 1.1 s and 1.26 GB peak per run, BiRefNet-lite 6 to 9 s and 5.4 GB, which does not fit the container. The Docker build downloads the model with a pinned URL and SHA-256 and places it in the image. Nothing is downloaded at runtime, so `OutboundPolicy` is not involved. The model loads on first use and is released after the job, so idle memory does not grow.

Memory: the app container limit in `deploy/compose.yaml` rises from 2 GB to 3 GB, and `MaxRAMPercentage` drops to 33 so the heap stays at about 1 GB. The added memory goes to native use by ONNX Runtime, Chromium and ffmpeg. The recovery probe, recovery options and recipe tests follow the new values. The host setup minimum rises from 4 GiB to 5 GiB, because the container limits then sum to about 4.2 GB. The new limit has to fit the owner's host CPU/RAM limit setup, without working around it. A CI test records peak container memory for one cutout and one trailer render on the fixture matchday.

Input is the first `STUDIO` image by `sortIndex`. The output is the original pixels with the predicted alpha mask, trimmed to the car's bounding box plus a margin.

Cutouts are produced:

- by a "Create cutout" button in the car's image gallery, which replaces an existing `AUTO` cutout
- automatically by the trailer job for every used car without a `CUTOUT`

The trailer uses the first `CUTOUT` by `sortIndex`. A `MANUAL` cutout placed before the `AUTO` one wins. Management can delete a bad automatic cutout or upload a better one.

Local development on macOS runs the same Java code. The Apple Vision script is not used. The model path is configured through `app.trailer.cutout-model-path`. A script under `scripts/` downloads the pinned model and checks its SHA-256 for local use. Without a model, the gallery button and the trailer checklist report "cutout model not installed" instead of failing later.

## Data model

Migration V48 and backup schema 26 (delivery 1), migration V49 and backup schema 27 (delivery 2):

- `Track.displayShortName`: optional, at most 30 characters, such as "SPA" or "ROAD ATLANTA". The track scene shows it large and the full name below. Without it, the full name is shown in a smaller size. It is editable in the track form.
- Car image check constraints gain `CUTOUT` and `AUTO` (V49), again as CASE-based checks.
- New table `music_tracks` (entity `MusicTrack`, 54 exported entities in total) with these fields:
  - `id`
  - `title` (required, at most 150 characters)
  - `artist` (required, at most 150 characters)
  - `credit` (optional free text for the YouTube description, at most 500 characters)
  - `filePath` (`/uploads/music/...`)
  - `durationSeconds` (read with ffprobe at upload)
  - `createdAt`

Backup and restore:

- Schema 26 exports the new field and table. Music files enter the archive through `enumerateReferencedUploads`.
- Schemas 1 to 25 restore without music and with a null short name. Schemas 1 to 26 carry no `CUTOUT` or `AUTO` values.
- Preflight before wipe checks the enum values, the at-most-one `AUTO` cutout per car, the music field limits and that every referenced music file is present.
- The read-only data audit reports missing music files and invalid cutout pairings without repair.

## Management UI

- **Music** (new admin page) lists the music tracks and offers MP3 upload with title, artist and credit, plus deletion. Uploads accept MP3 up to 20 MB, checked by magic bytes. Native forms with CSRF.
- **Matchday detail page and each round row of the playoff bracket page** get a "Trailer" section with:
  - the precondition checklist with links
  - a music select (one track or "Random")
  - a start button, disabled while the checklist is open or a job runs
  - live status with scene progress
  - downloads for MP4, ZIP and YouTube text when a trailer exists
- **Car gallery** gets the "Create cutout" button.
- **Track form** gets the short name field.

All controls work without JavaScript. Labels, field errors and focus handling follow the existing editor conventions.

## Tests

- Unit:
  - the scene plan (dedup of identical legs, ordering, durations)
  - the checklist rules, including unpublished setup and missing images
  - the ffmpeg argument builder with pinned expected arguments
  - YouTube text and credits
- Cutout: a small fixture image through the real ONNX session asserts alpha presence and bounding-box trimming.
- CI: the worker that runs the cutout and trailer tests installs ffmpeg and fetches the pinned model with SHA-256 check and a cache keyed by that hash. Every test executes in its planned partition, as the CI partition contract requires; no conditional skips.
- Integration:
  - V48 and V49 constraints on H2 and MariaDB
  - backup round trip with music files
  - preflight rejections
  - audit findings
  - a full trailer job on a small fixture matchday, which produces an MP4 with expected duration, resolution and an audio stream, checked with ffprobe
- Browser: Music upload and delete, the trailer section without JavaScript, the disabled start with an open checklist.
- Docker: an image smoke test runs `ffmpeg -version` and loads the cutout model.

## Delivery

Prerequisite: the car and track image gallery is delivered.

1. ffmpeg in the image and CI, fonts, `displayShortName`, music library with backup, preflight and audit. Music duration is read with ffprobe.
2. Car cutouts: container limit 3 GB, ONNX model in the image and CI, `CUTOUT`/`AUTO` kinds, the single-job executor, the gallery button.
3. Scene templates and the scene plan, with PNG ZIP output only.
4. Video composition, trailer job, trailer section and downloads.

Each delivery updates `CLAUDE.md`, `README.md` and the wiki.

## Approaches considered

- **Output.** Finished MP4 (chosen) versus a PNG package for manual editing. The ZIP is kept as a by-product.
- **Motion.** Playwright layers plus ffmpeg filters (chosen) versus frame-by-frame HTML rendering. Frame-by-frame would allow any CSS animation, but costs 5 to 10 minutes of Chromium load per trailer. Slides, zooms and fades are enough.
- **Cutout.** ONNX Runtime in Java (chosen); a Python `rembg` sidecar (second service and image); classic colour keying plus GrabCut (fails on black cars); macOS Vision (not available on the Linux box).
- **Memory.** Container limit raised to 3 GB (chosen) versus staying at 2 GB with a smaller model or input size.
- **Music.** Library table with backup (chosen) versus a plain folder on the volume (no credits, not in backups).
- **Scenes.** A combined track + studio-cutout scene plus a short car-in-action scene from a GT7 scene image per race (chosen). Cutting the car out of scene images was tested and dropped: motion blur and dark tyres left ghosting on one of four cars.

## Left to the builder

- The exact ffmpeg filter graph, easing and transition types.
- The exact layout of each scene within the look above.
- Choice between BiRefNet-lite and ISNet, based on the comparison described above.
- The YouTube title and description wording.
