# Discord `/stats` command

Every member of the CTC guild can query driver and team statistics with `/stats driver` and `/stats team`. The command runs on the existing Gateway. It checks no roles and no channel bindings, because all figures are already public on the website. All user-facing text is English.

The pattern follows the `/driver` command of the CLR bot (`de.jegr.clr.discord.drivers`): name autocomplete with the entity ID as value, an exact-name fallback and one embed per answer.

## Commands

```
/stats driver name:<autocomplete> [scope:current|alltime|season] [season:<autocomplete>]
/stats team   name:<autocomplete> [scope:current|alltime|season] [season:<autocomplete>]
```

- `name` is required in both subcommands. There is no "me" default.
- `scope` defaults to `current`, the active season. If no season is active, the answer uses `alltime` and says so.
- `season` is required with `scope:season` and ignored otherwise.
- The command is available in every guild channel.

## Seasons

The bot counts the same seasons as the public website. It excludes seasons whose name contains "Test" and seasons without a REGULAR phase. The current season is `Season.active`. Season order follows year and number, never the name.

## Autocomplete and lookup

- Driver choices match PSN ID, nickname and `PsnAlias`. Team choices match name and short name.
- Each choice shows a readable label of at most 100 characters and returns the UUID as value. At most 25 choices are returned.
- With `scope:season` and a chosen season, choices contain only participants of that season. Otherwise they contain every driver with at least one counted result and every team with at least one match.
- Season choices show `Season.getDisplayLabel()` and return the season UUID.
- Command execution accepts the UUID first, then an exact case-insensitive name.
- Ephemeral error replies:
  - "No driver found" / "No team found"
  - "Several drivers match, pick one from the list" / "Several teams match, pick one from the list"
  - "No results for <name> in <season>"
- Autocomplete failures return an empty list and are logged.

## Counting rules

- Results of races with a walkover (`WalkoverScoring.countsForDriverStatistics`) never count.
- Playoffs count in every total. There is no regular/playoff split.
- Disqualifications:
  - A DSQ result counts as a race.
  - Wins and podiums use the classification position from `RaceClassification`. A driver who moves up to P1 after a DSQ ahead of them has a win.
  - The average position ignores DSQ results.
  - The DSQ count appears when it is greater than zero.
- A pole is `qualiPosition == 1`, also for a later DSQ. A fastest lap is `fastestLap == true`.
- Guest appearances count for the driver, and their number appears when it is greater than zero. For the team, races count through its matches, including races with guest drivers.
- Subteams follow the existing standings: a subteam is its own entry in a season, and alltime figures roll up to the parent through `getParentOrSelf()`.
- Team match figures reuse `StandingsService`, including its walkover and bye rules.
- A playoff title belongs to the winner of the matchup in the playoff round with the highest `roundIndex`.

## Driver embed

- Title: PSN ID, linked to the profile page when a base URL is configured.
- Scope line: "Alltime" or the season display label.
- Team:
  - season scope: the team of that season, from `RaceLineup` with the season roster as fallback
  - alltime: the last three teams in chronological order, then "+N" for more
- Rank: "Rank 4 of 62 · 412 pts", from the driver ranking of the scope.
- Races: races · wins · podiums · poles · fastest laps.
- Positions: best result · average position with one decimal.
- Points: total · average per race.
- Only when greater than zero: guest races, DSQs.
- Alltime only: number of seasons.

## Team embed

- Title: team name, linked to the profile page when a base URL is configured.
- Rank:
  - season scope: regular-season rank, with the group when the phase has groups, e.g. "Rank 3 of 10 · Group A"
  - alltime: rank in the alltime standings
- Matches: played · wins · draws · losses · win rate.
- Points: scored · conceded · difference · match points.
- Races: number of races.
- Season scope: playoff result when one exists, e.g. "Semi-final" or "Champion".
- Alltime: seasons · best regular-season result with its season · playoff appearances · titles.
- Top drivers: the three drivers of the team with the most points in the scope.

## Reply and sharing

- Every answer is an ephemeral embed with empty allowed mentions and Markdown-escaped names.
- The embed carries a "Share in channel" button. Its custom ID encodes kind, entity UUID, scope and season UUID, within the 100-character limit.
- A click recomputes the figures and posts the embed publicly in the channel with the text "Shared by <display name>", without a mention.
- The button holds no server state and does not expire. An entity that no longer exists, for example after a restore, produces the ephemeral reply "No longer available".
- Figures come from the live database, so they can be newer than the published website.

## Website links

A new property `ctc.site.public-base-url` holds the public site URL. Without it, embeds carry no link. The hard-coded Pages URL in the site publication code moves to this property.

- season scope: the driver or team profile of that season, using the stored `SiteSlug`
- alltime: the profile in the newest counted season, as the site's "latest season profile" logic chooses it

## Gateway integration

- New setting `app.discord.stats.enabled`, default false.
- The Gateway starts when picks or stats are enabled. Picks and incidents handling stays tied to its existing switch and checks.
- The JDA transport forwards slash-command options, subcommand names and autocomplete events. The dispatcher allowlist gains `stats` and the `ctc-stats:` component prefix.
- Stats work runs on the existing bounded workers after the private acknowledgement. Autocomplete answers directly within Discord's three-second limit.
- While a restore runs, the command answers "Stats are unavailable during maintenance", like the other commands.
- Stats services are read-only (`@Transactional(readOnly = true)`) and write nothing.

## Registration

A "Register /stats" button in the Discord management page posts the single `/stats` command definition to `/applications/{id}/guilds/{guild}/commands`. Discord creates or updates a command by name and leaves `/picks` and `/incidents` untouched. The command is never registered on connect.

## Tests

- The stats service is tested against synthetic `Test`-prefixed fixtures. These cover every counting rule: DSQ ahead, pole with DSQ, walkover exclusion, guest races, playoffs, subteam roll-up, Test-season exclusion and the absence of an active season.
- Interaction handling sits behind small interfaces, so tests drive commands, autocomplete and the share button without a Discord connection.
- A unit test pins the production command JSON, including subcommands, option types and the autocomplete flags.
- Tests cover the escaping of names, label truncation, empty allowed mentions and the custom-ID length.

## Delivery

Three pull requests, each with its own sub-issue:

1. Gateway decoupling, `app.discord.stats.enabled`, options and autocomplete in the transport, dispatcher routing and the registration button.
2. Stats service with the counting rules, and `/stats driver` with autocomplete and the embed.
3. `/stats team`, the share button and website links with `ctc.site.public-base-url`.

Each delivery updates `CLAUDE.md`, `README.md`, `docs/operations/discord-integration.md` and the wiki.

## Out of scope

- Rendered image answers.
- Linking Discord users to drivers.
- Comparing two drivers or teams.
- Changes to how the website ranks drivers.
