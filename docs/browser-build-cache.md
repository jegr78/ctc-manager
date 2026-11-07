# Browser dependency caching

CI resolves its real dependency classpath before selecting a browser cache. `scripts/ci/browser-cache.py` reads the Maven metadata inside the actual Playwright, driver and driver-bundle JARs. All three must identify the same pinned release. Missing, duplicate, unpinned or mismatched dependencies fail before cache selection.

The key includes the resolved version, OS, CPU architecture and distribution/OS release. Kernel updates and application source/version changes do not change it. CI's complete browser set and the release workflow's Chromium-only set use different suffixes. There are no cross-version or cross-platform restore prefixes. Installation still runs before tests, including system dependencies and all browsers already required by each workflow. A cache hit does not authorize skipping runtime prerequisites or dropping browser installations.

The Docker dependency stage resolves the existing POM and copies only Playwright's dependency JARs to a stable directory. `browser-runtime` mounts these inputs read-only while installing Chromium and running the non-root screenshot smoke test. The application JAR is copied only in the final runtime stage. Application changes therefore do not invalidate browser installation or its smoke test. A changed Playwright JAR or JRE/OS base remains an input change to that stage. The mounted helper JARs do not create an additional permanent copy in the runtime image; the application retains its normal dependencies. See [Docker cache invalidation](https://docs.docker.com/build/cache/invalidation/).

The Noble base tags, non-root `ctc` user, persistent directory ownership, runtime entrypoint and existing native recovery verification remain required. Browser downloads and dependencies are installed on cold builds; an earlier image/cache is never a required build input. See [Playwright Java Docker](https://playwright.dev/java/docs/docker).

Use complete build logs to compare a cold build, an application-only rebuild and a browser dependency change. Record the source/tree, base images, resolved version, cache state, browser-step cache hits, elapsed time and produced image identity. Distinguish downloaded browser caches, Docker layers and Maven dependencies; a warm browser layer does not imply a cold Maven build became fast. Final cumulative initiative measurements belong here after the complete reviewed CI and image verification, with unmet targets stated explicitly. No Box/Mac deployment or database work is involved.

## Recorded measurements

The controlled Docker probe used source `bd3db60d578f85a444916e9d56c31110d3f51c09` on Linux amd64, 8 logical CPUs and 15 GiB RAM. It used a private archive of the committed source, without deploying an application. The cold command forced all build instructions with `--no-cache`; base images could already be local.

| Probe | Scope | Wall time | Browser installation and smoke |
| --- | --- | ---: | --- |
| Cold candidate, Playwright 1.64.0 | Complete image | 306.302 s | Both executed |
| App-only CSS change | Complete changed image | 85.058 s | Both cached |
| Private dependency change to 1.63.0 | `browser-runtime` stage only | 266.835 s | Both executed again |

The app-only probe produced a different image while preserving the installed browser stage. The dependency-change probe builds only the browser stage, so its duration is not a complete application-build comparison. The candidate retains Playwright 1.64.0. Both complete images retain `ctc` as runtime user and `java -jar ctc-manager.jar` as entrypoint. The existing native private-storage acceptance passed against the cold candidate: mode 0700, owner-readable exact inventory, rejected filesystem-sync failure and completed cleanup. Docker provenance records the actual pinned base-image digests.

The complete CI progression before test distribution was:

| Delivered source | Maven verification | Line coverage |
| --- | ---: | ---: |
| PR #367, `881802b8` | 38:11 | 93.92% |
| Fixture isolation, PR #374, `0ee63faa` | 34:23 | 93.92% |
| Startup rendering, PR #375, `fa841ec1` | 28:40 | 93.91% |

These are individual successful GitHub-hosted runner observations, not repeated medians. The #375 run executed 3,665 unit, 2,458 integration and 257 browser cases without failures/errors/skips. Its XML contains all integration cases, although suite header counters undercount two same-named nested cases. Test distribution checks actual case elements and exact JUnit execution IDs instead of relying on those headers.

The earlier initiative record reports a roughly 72-minute complete local baseline. Focused local examples are recorded separately in [Local verification](local-verification.md); they do not establish the final full-run target. The complete final local result, cumulative CI critical path/runner time, test inventory and reviewed source SHA are recorded in [PR #378](https://github.com/jegr78/ctc-manager/pull/378) and its checks. Targets below 45 minutes locally and 20 minutes in CI require those complete measurements. Queue time, dependency/browser caches and selected-versus-complete scope must remain explicit when comparing them.
