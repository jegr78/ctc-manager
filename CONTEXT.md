# CTC racing and confidential submissions

CTC seasons organize team races. Home teams provide private race choices for management review.
Individual reporters retain their own incident cases under a separate reporting deadline.

## Language

**Season rulebook**:
The racing regulations belonging to one CTC season, including that season after it is archived.
_Avoid_: Current global rules

**Season racing etiquette**:
The driving conduct guidance applicable to one CTC season, maintained separately from its rulebook.
_Avoid_: Shared global rules

**Driver disqualification (DSQ)**:
A race-specific exclusion from race-position points. The driver's qualifying and fastest-lap bonuses remain; the separate team penalty is entered by management.
_Avoid_: Zero total points, deletion of the result

**Recorded finishing position**:
The original finishing position supplied for a driver in a race, retained when a disqualification is set or cleared.
_Avoid_: Effective classification

**Effective classification**:
The recorded finishing order after excluding disqualified drivers for race-position points and classified results. Removing a disqualification restores the corresponding positions and points.
_Avoid_: Edited original finishing position

**Race**:
One leg in which every participating driver uses the same concrete car and race setup.
_Avoid_: Match

**Matchday**:
A round containing multiple team pairings, with one shared pick opening and an ordinary pick deadline.
_Avoid_: Match, race

**Match**:
A pairing of teams that contains one or more race legs.
_Avoid_: Race

**Effective home team**:
The team responsible for a race's choices, including a return leg that reverses the match's teams.
_Avoid_: Original pairing home, parent team

**One-make car**:
The single concrete vehicle used by every participating driver in a race.
_Avoid_: Per-driver car

**Pool entry**:
A named seasonal choice defined by alternative car conditions or an explicit One-Make, consumed
once per effective home team. Its frozen membership stays fixed when additional entries expand
the season pool.
_Avoid_: Concrete car

**Pick**:
The home team's private selection process for one scheduled race, including confirmation and
management review.
_Avoid_: Vote, ballot

**Draft**:
An unfinished editing attempt for a pick, separate from every confirmed submission.
_Avoid_: Revision

**Revision**:
One immutable complete version of a pick confirmed by a team submitter.
_Avoid_: Draft, current race setup

**Pending pick**:
A confirmed pick awaiting approval, including a returned pick or one whose team collection is closed.
_Avoid_: Unfinished draft

**Reservation**:
A pending pick's claim on its car or pool entry and track for its effective home team in that season.
_Avoid_: Publication, approval

**Pick deadline**:
The ordinary team-submission cutoff shared by a matchday’s matches unless an individual match has a deadline override. The exact cutoff moment is already closed.
_Avoid_: Approval deadline, publication time

**Pick opening**:
Management's manual opening of a whole matchday for team picks. A planned matchday can remain closed; opening never bypasses the applicable deadline.
_Avoid_: Per-race opening, scheduled start, deadline override

**Active matchday**:
A matchday explicitly opened for picks that has not been closed or completed all its matches.
_Avoid_: Prepared matchday, current season

**Match pick-deadline override**:
An individual pairing's submission cutoff that replaces the matchday's ordinary cutoff for that match's race legs.
_Avoid_: Matchday opening, individual race deadline

**Legacy manual opening**:
A historical management exception that opened one unapproved pick independently of its ordinary cutoff.
_Avoid_: Matchday pick opening

**Pick closing**:
Closure of a whole matchday's team-pick collection, either by management or after every match is completed.
_Avoid_: Deadline expiry, individual race closure

**Legacy manual close**:
A historical management decision that prevented further team confirmations for one unapproved pick.
_Avoid_: Matchday pick closing

**Approval**:
Management acceptance of one confirmed revision as the race's current setup.
_Avoid_: Publication

**Publication**:
Confirmed disclosure of a particular content version through an explicit management action.
_Avoid_: Approval, deadline expiry

**Published pick content**:
One frozen version of a race's choices to which confirmed disclosures belong.
_Avoid_: Current internal setup, latest submission

**Disclosed field**:
A choice value from a particular published content version that management has confirmed as public.
_Avoid_: Approved field, complete payload release

**Publication attempt**:
One explicit management request to disclose captured race choices, with its confirmed, failed or uncertain outcome.
_Avoid_: Successful publication, automatic preview edit

**Internal correction**:
A management change to an approved race setup that retains its original approved submission and team lock.
It remains private until management explicitly publishes the corrected content.
_Avoid_: Undo approval, team resubmission, automatic publication

**Managed race**:
A race enrolled for confidential team picks, whose private history remains protected when collection stops.
_Avoid_: Currently enabled collector

**Submission receipt**:
The durable confirmation of a particular completed submission that its team can recover after a lost reply.
_Avoid_: Unfinished wizard response


**Private team channel binding**:
Management's explicit association between a team's private Discord channel and that team in a
particular season. Current channel access authorizes its members to use that scope.
_Avoid_: Shared match channel, inherited team role

**Incident match context**:
The single reporting scope for a real team pairing and its concrete race legs, including a
playoff pairing that is also shown through a regular-match view.
_Avoid_: Separate case register per view

**Reporting window**:
Management's explicitly opened period for incident reports at one pairing, with a confirmed
cutoff that later schedule changes do not move.
_Avoid_: Pick deadline, unlimited late opening

**Incident report**:
A permanent case for one person's confirmed account of an incident in a concrete race.
It remains a case after withdrawal and after its discussion moves to Discord.
_Avoid_: Transient form answer, discussion thread

**Reporter**:
The person who created an incident report and alone may ordinarily edit or withdraw it before
the reporting cutoff. Current channel access and eligibility for the original reporting party
remain necessary; sharing a team role does not grant another creator's private history.
_Avoid_: Any teammate, reporting organization

**Reporting party**:
The team or shared organization attributed to an incident report, retained as it was at first
confirmation and distinct from its individual reporter.
_Avoid_: User-selected side, public reporter identity

**Reporting-party candidate**:
The current team or shared organization resolved from the invoking person's relevant match
roles for one private draft. Confirmation captures the definitive reporting party; later role
or label changes never rewrite the saved snapshot.
_Avoid_: User-selected team, frozen current role configuration

**Incident revision**:
One immutable complete confirmed version of an incident report's facts and evidence references.
_Avoid_: Unfinished dialog, later Discord discussion

**Incident withdrawal**:
The reporter's decision before the cutoff to remove a case from active disclosure while
retaining the case and its confirmed history.
_Avoid_: Deletion, deadline extension


**Neutral incident receipt**:
A detail-free public signal that a person's first confirmed report was stored. It reveals no
reporter, party, drivers, replay time, description or evidence. Edits and withdrawal do not
produce another receipt; an earlier receipt remains historical after withdrawal.
_Avoid_: Active-case count, disclosure starter

**Incident delivery operation**:
One permanent request to publish either the neutral receipt or the final post-cutoff disclosure
for a report, retaining its own immutable revision and destination. A saved report and successful
public delivery are separate outcomes. Unknown remote results require reviewed reconciliation.
_Avoid_: Exactly-once Discord guarantee, transient job

**Frozen incident disclosure**:
The final active revision captured when a reporting window closes under its stored cutoff.
It remains separate from the earlier neutral receipt and excludes withdrawn reports.
_Avoid_: Live report projection, unfinished draft
