# Tasks

## 1. Backend

- [ ] 1.1 `PartyStatistics` with `quelea.parties.created`, `quelea.parties.disbanded{reason}`,
      `quelea.party.lifetime{reason}` (SLO buckets) and `quelea.party.size` (buckets 2..8).
- [ ] 1.2 Calls from `PartyService.create`, `accept` and `disbandParty`.
- [ ] 1.3 `quelea.players.online` gauge from the presence store (port method + in-memory fake + Redis adapter).
- [ ] 1.4 Tests with a `SimpleMeterRegistry` and a fixed `Clock` (creation counted, disband by reason with lifetime,
      size on join, players online across proxies); Redis integration test for the count.

## 2. Documentation

- [ ] 2.1 README: the new metrics in the operations section.

## 3. Pull request

- [ ] 3.1 Open the code pull request `feat(observability): record party statistics`.
