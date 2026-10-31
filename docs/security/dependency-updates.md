# Dependency updates and daily security checks

## Requirement matrix

| Requirement | Production path | Positive verification | Failure verification | Runtime evidence |
| --- | --- | --- | --- | --- |
| Daily update eligibility, including majors | renovate.json | official strict config validator | native regex validation | hosted Renovate dashboard and PRs |
| Prompt vulnerability fixes | Renovate GitHub/OSV alerts | graph/alerts enabled and supported Maven datasource | alert API failures remain visible | GitHub security status |
| Daily persistent CodeQL finding detection | codeql.yml, codeql-gate.sh | clean scheduled/manual scan passes | existing HIGH/CRITICAL and API failure reject | daily workflow and manual dispatch |
| Deployment database digest stays maintained | tagged database-image and release parser | compatible tagged digest packages correctly | mismatched pin rejects | Renovate extracts tag/digest together |

Renovate may propose updates every day without a time window or PR throttling. Major updates
need no dashboard approval and remain ordinary reviewed PRs. Patch automerge retains the
existing requirement for successful repository checks. GitHub alerts and Renovate OSV checks
are complementary. OSV support currently covers direct Maven dependencies; it does not prove
that every transitive library or OS package is free of vulnerabilities.

The hosted Renovate backend controls its actual execution frequency. A repository schedule
only permits or restricts runs; it cannot force the hosted service to start at a chosen minute.

CodeQL runs daily at 02:00 UTC and on existing push/PR triggers. Scheduled and manual scans fail
while any undismissed HIGH/CRITICAL alert remains open. Push and PR checks continue to reject
new findings against their baseline. API failures fail the check. Review and document false
positives using the existing SAST acceptance process rather than hiding them in configuration.

The production MariaDB reference carries a version tag and digest so Renovate can maintain it.
Deployments still select immutable image identities and apply the explicit database migration
procedure when a major database transition is required.

## Resolved dependency graph

The Dependency Security workflow submits the complete Maven graph, including transitive
libraries, after relevant trusted master pushes and daily at 01:30 UTC. GitHub's existing
Dependabot alerts and security updates use that graph. The submission job receives contents
write only for the graph API and does not run on pull requests. Its pinned GitHub-maintained
action uses the repository's Maven wrapper. A submission failure stays visible as a failed job.

Initial audit: GitHub vulnerability alerts and security updates were already enabled, with no
open Dependabot alerts. The existing SBOM listed 50 Maven packages. Record the effective graph
and first successful submission after rollout rather than claiming complete coverage from
that initial static inventory.

## Verification

The strict validator in the official Renovate 44.198.3 image accepted the configuration with
native regex support. CodeQL daily/manual behavior passed twelve unit cases. Both new daily
finding and API-failure guards were individually falsified, failed at the intended assertions,
and restored from exact Git snapshots. The deployment bundle suite covers tagged MariaDB
pins and compatibility with earlier untagged archive references. Run the complete repository
verification on the final committed branch after review and upstream integration.

A network-isolated local-platform extraction with the official Renovate image detected the
production file as mariadb version13 with its exact SHA-256 digest. This proves the new
reference is discoverable without granting the validator repository or host credentials.
