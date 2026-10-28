# Problem codes

Every error response of the Quelea service is an RFC 9457 problem document with media type
`application/problem+json`. Its `code` field is a stable, machine-readable slug; its `type` URI ends
with the same slug and links to the section below. Clients switch on `code`, not on `title` or `detail`.

Common fields: `type`, `title`, `status`, `detail`, `instance` (the request path), `code`, `traceId`
(present when tracing is active; quote it when reporting a problem), and `errors` (only for
`validation-failed`).

## validation-failed

- **Status:** 400
- **When:** the request body is not valid JSON, a required parameter is missing or does not convert,
  or a field fails validation (for example an empty chat message). The `errors` list names each field.
- **What to do:** fix the request. Do not retry it unchanged.

## not-found

- **Status:** 404
- **When:** no route matches the request path.
- **What to do:** check the path and the API version against the OpenAPI document of the service.

## method-not-allowed

- **Status:** 405
- **When:** the path exists, but not for this HTTP method.
- **What to do:** use the method listed in the OpenAPI document for that path.

## unsupported-media-type

- **Status:** 415
- **When:** the request body is sent with a content type other than `application/json`.
- **What to do:** send the body as JSON with `Content-Type: application/json`.

## party-not-found

- **Status:** 404
- **When:** the party id in the path does not exist, usually because it was disbanded or expired.
- **What to do:** stop using the id. Read the player's current party with the player's party endpoint.

## not-in-party

- **Status:** 404
- **When:** the player is not a member of any party (for example when reading a player's party or
  leaving a party the player is no longer in).
- **What to do:** treat the player as solo. Nothing needs to be cleaned up.

## already-in-party

- **Status:** 409
- **When:** a player who is already in a party creates or accepts a party, or two accepts race for the
  same player and only one wins.
- **What to do:** tell the player to leave the current party first. Do not retry.

## not-leader

- **Status:** 403
- **When:** a member who is not the leader kicks, promotes, disbands, warps or invites.
- **What to do:** tell the player that only the leader may do this.

## not-member

- **Status:** 403
- **When:** a player who is not in the party sends a chat message to it or acts on it as a member.
- **What to do:** tell the player that they are not in this party.

## party-full

- **Status:** 409
- **When:** the party already has the configured maximum number of members (`QUELEA_PARTY_MAX_SIZE`,
  default 8, counting the leader), so an invite or an accept is refused.
- **What to do:** the leader must kick someone first, or the invite must be dropped.

## player-not-online

- **Status:** 404
- **When:** the invite target name is not online on any proxy.
- **What to do:** invite again once the player is online. Names are matched case-insensitively.

## invite-not-found

- **Status:** 404
- **When:** the player has no pending invite for that party, or the invite has expired (the invite TTL
  is `QUELEA_INVITE_TTL`, default 60 seconds).
- **What to do:** ask the leader to invite the player again.

## cannot-target-self

- **Status:** 400
- **When:** a leader kicks or promotes themselves, or a player invites themselves.
- **What to do:** choose another player.

## lock-timeout

- **Status:** 503
- **When:** another request held the party's lock for longer than the wait limit, so this one could
  not run. The state was not changed.
- **What to do:** retry after a short delay. The request is safe to repeat.

## internal-error

- **Status:** 500
- **When:** an unexpected failure happened inside the service. The detail is always the fixed text
  "An unexpected error occurred."; the cause is only in the service logs, under the `traceId`.
- **What to do:** retry once. If it persists, report the `traceId` to the operators.
