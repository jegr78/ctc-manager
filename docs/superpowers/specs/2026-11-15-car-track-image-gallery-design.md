# Car and track image galleries

Cars and tracks get several images each, so that future graphic and video formats can use them. Management uploads images by hand for cars and tracks. For a car with a GT7 ID, management can additionally load all official GT7 car shots with one button. The standard GT7 sync stays unchanged.

## Sources

### GT7 car shots

The car detail page `https://www.gran-turismo.com/gb/gt7/carlist/id/car<gt7Id>` renders in the browser. Its images are referenced in the same `index-*.js` bundle that `Gt7ScraperService` already loads for the car list:

```
"../../generated-assets/images/car_shots/2k/car365_1_01.jpg":()=>H(()=>import(`./car365_1_01-3T8vbnCx.js`)
```

The referenced module contains only the hashed image path:

```
var e=`/common/dist/gt7/carlist/assets/car365_1_01-B9kseron.jpg`;export{e as default};
```

Resolution per car: load the bundle, select every `car_shots/2k/car<gt7Id>_<slot>_<nr>.jpg` entry, fetch its module from `/common/dist/gt7/carlist/assets/` and download the referenced JPEG. Only 2K images are used (1920×1080, about 300 KB). 4K entries are ignored.

On 2026-11-15 the bundle listed 580 cars:

| Slot | Content | Kind | Cars |
|---|---|---|---|
| `1_01`, `1_02`, `1_03` | car on track | `SCENE` | 580 / 174 / 3 |
| `2_01`, `2_02` | studio shot on a dark gradient | `STUDIO` | 580 / 580 |
| `3_01`, `3_02` | cockpit | `COCKPIT` | 564 / 2 |

Every slot found is loaded. An unknown slot prefix maps to `OTHER`. A car without shots in the bundle produces a visible message and changes nothing.

### Tracks

The GT7 track list offers only logos. Track images come from manual uploads. Third-party sources such as dg-edge.com are out of scope because their terms allow only personal use.

## Data model

Migration V45 adds two tables, which brings the entity count to 50. Backup schema 23 exports both.

`CarImage` and `TrackImage` share these fields:

- `id`, owning `car` or `track` (required FK, cascade delete with the owner)
- `kind`:
  - cars: `STUDIO`, `SCENE`, `COCKPIT`, `OTHER`
  - tracks: `BACKGROUND`, `LAYOUT`, `OTHER`
- `source`: `GT7` or `MANUAL`
- `sourceKey`: the GT7 slot such as `2_01`. Required for `GT7`, null for `MANUAL`. Unique per car and source.
- `label`: optional free text, at most 100 characters
- `imagePath`: local `/uploads/...` path
- `width`, `height`: read from the stored file
- `sortIndex`: order within the owner
- `createdAt`

Enumerated values use CASE-based check constraints, which keep H2 and MariaDB valid after the migration connection closes.

The existing `Car.imageUrl` thumbnail and `Track.imageUrl` logo stay unchanged and separate.

A service method returns the first image of a requested kind by `sortIndex`. Future graphics use it. This scope changes no graphic template and no public page.

## Management UI

Car and track forms get an "Images" fieldset with:

- a thumbnail grid showing kind, source, label and size
- an upload form for several files at once, with kind and optional label
- a delete action per image, including GT7 images
- up and down buttons for the order, with labeled controls, retained focus and position announcements
- on cars with a GT7 ID: a "Load GT7 images" button that reports added, replaced and unchanged slots

Uploads accept JPEG, PNG and WebP up to 10 MB, like the existing image upload. All controls are native forms with CSRF and work without JavaScript. Errors keep the form context.

## GT7 loading

- Triggered per car only. The standard sync and its preview do not load car shots.
- A repeated load replaces `GT7` images with the same slot, adds missing slots and leaves `MANUAL` images untouched. A replaced file is removed with `FileStorageService.deleteAfterCommit`. A new file is removed on rollback.
- Downloads happen outside the database transaction. The images are then written on the transaction thread after a fresh read of the car.
- The action requires `OutboundPolicy` and the hardened download path below.

## Download hardening (prerequisite)

`FileStorageService.storeFromUrl` gets these guards for every caller, including the existing thumbnails and track logos:

- host allowlist: `www.gran-turismo.com` only
- no redirects
- connect and read timeouts
- maximum size, 15 MB
- content check by magic bytes against the expected image type
- rejection of resolved loopback, private, link-local and multicast addresses

## Backup, restore and audit

- Both tables are exported with every field. Their files are added to the archive through `enumerateReferencedUploads`.
- Schema 23 requires both files, even when empty. Schemas 1 to 22 restore without images.
- Preflight before wipe checks:
  - owner references
  - enum values
  - the `sourceKey`/`source` pairing
  - uniqueness of GT7 slots per car
  - that every referenced file is present in the archive
- The read-only data audit reports missing files and invalid pairings without repair.

## Tests

- Scraper: a fixture bundle and module files pin the production formats, without live network access. Covered cases: slot parsing, the 2K filter and unknown slots.
- Hardening: allowlist, redirect, size, magic bytes and private-address rejection.
- Service: replacement of GT7 slots, protection of manual images, `sortIndex` handling, first image by kind, rollback cleanup.
- Integration: migration constraints on H2 and MariaDB, backup round trip with files, preflight rejections, audit findings.
- Browser: upload, reorder and delete with keyboard and without JavaScript.

## Delivery

1. Download hardening for `storeFromUrl`.
2. Image tables, galleries and manual upload for cars and tracks, including backup, preflight and audit.
3. "Load GT7 images" per car.

Each delivery updates `CLAUDE.md`, `README.md` and the wiki.

## Out of scope

- 4K images.
- A bulk action for all cars.
- Automatic track images from third parties.
- Use in graphic templates, videos or public pages.
