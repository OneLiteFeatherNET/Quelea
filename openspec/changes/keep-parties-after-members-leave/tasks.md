# Tasks

## 1. Stored alone-since time (backend)

- [ ] 1.1 Failing test first: a `Party` stored without `aloneSince` loads with the field `null` and its alone end is `createdAt` plus the timeout.
- [ ] 1.2 Add the nullable `aloneSince` to `Party`; keep the existing constructor call sites compiling; set it on create and clear it on join.
- [ ] 1.3 Redis round-trip test (Testcontainers, `integration`): a party with and without `aloneSince` serialises and deserialises.

## 2. Party stays open after a departure (backend)

- [ ] 2.1 Failing unit test first, fixed `Clock`: leave from a two-member party leaves one member, same id, no `party.disbanded`, alone period starts at the leave.
- [ ] 2.2 Failing unit test: the leader leaves, the longest-standing member becomes leader and `party.leader-changed` is published.
- [ ] 2.3 Failing unit test: a kick that leaves one member behaves like 2.1.
- [ ] 2.4 Failing unit test: a leave from a one-member party still disbands with `last-member-left`.
- [ ] 2.5 Implement in `removeMember`: one remaining member keeps the party, saved with `aloneSince` set to now and marked alone; zero remaining members keep the disband path.
- [ ] 2.6 Accept clears the alone period on the party and in the alone sets, as before.

## 3. Expiry and warning use the alone period (backend)

- [ ] 3.1 Failing unit test, fixed `Clock`: a party alone since 10:00 gets a warning at 10:25 with `expiresAt` 10:30; a party created at 09:00 and never joined keeps the old schedule.
- [ ] 3.2 Failing unit test: a party that is alone again after a join restarts the timer from the new moment, not from creation.
- [ ] 3.3 Failing unit test: a legacy party without `aloneSince` expires by `createdAt`.
- [ ] 3.4 `markAlone` computes the expiry from `aloneSince ?? createdAt`; `warnExpiring` takes `expiresAt` from the same value.
- [ ] 3.5 Redis adapter: `markAlone` with a null warning time removes any existing warning entry. Integration test (`integration`) that a restarted alone period without warning is not warned at the old time.

## 4. Velocity plugin

- [ ] 4.1 Failing test first: after `party.left` or `party.kicked` leaves one member, the remaining member gets the `party.left.alone` message; with more members nothing changes.
- [ ] 4.2 Add `party.left.alone` (and `party.kicked.alone` if the kick wording needs it) to `messages_en.properties` and `messages_de.properties`, and to the `MessagesTest` key list.
- [ ] 4.3 `EventHandler`: send the key to the single remaining member; keep the generic messages for everyone else.

## 5. Documentation

- [ ] 5.1 README: the alone timeout now applies after a leave or kick that leaves one member; the disband reason `last-member-left` applies only when no member remains.

## 6. Pull request

- [ ] 6.1 Open the code pull request `fix(party): keep a party alive after its other members leave` from a branch off the merged proposal, with no file under `openspec/`.
