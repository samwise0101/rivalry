# Data Loading Components

## RosterResolver

File: `src/main/java/com/samwise0101/rivalry/RosterResolver.java`

`RosterResolver` produces the ordered list of players to track.

Manual mode:

- Starts with local player name, if known.
- Adds `rival1` through `rival5`.
- Trims input.
- Skips blank names.
- Deduplicates case-insensitively.

Wise Old Man mode:

- Requires `womGroupId() > 0`.
- Calls `WomClient.fetchGroupMembers(groupId, maxMembers, ...)`.
- Prepends local player to WOM members.
- Deduplicates case-insensitively.
- Stores the last successful WOM members in `lastWomMembers`.
- On fetch failure, falls back to `lastWomMembers` if available.
- Without fallback, completes exceptionally with `RosterException`.

`RosterException` messages are user-facing and shown in the panel.

## WomClient

File: `src/main/java/com/samwise0101/rivalry/WomClient.java`

`WomClient` fetches group membership from Wise Old Man.

External components:

- Injected `OkHttpClient`.
- Injected `Gson`.
- `okhttp3.HttpUrl`, `Request`, `Callback`, `Response`.

Request details:

- Base URL: `https://api.wiseoldman.net/v2`.
- Endpoint: `/groups/{groupId}`.
- User-Agent: `Rivalry RuneLite Plugin - https://github.com/samwise0101/rivalry`.
- Uses `enqueue()`, so network I/O is not on the client thread.

Response details:

- Non-successful response or missing body calls `onError`.
- JSON is parsed into minimal nested model classes.
- Unknown fields are ignored by Gson.

Member selection:

- `selectMembers()` is pure and unit tested.
- Sorts by `lastChangedAt` descending.
- Null timestamps sort last.
- Uses `displayName` with `username` fallback.
- Applies `Math.max(1, maxMembers)`.

Change guidance:

- Keep HTTP on injected OkHttp.
- Keep JSON on injected Gson.
- Put pure filtering/sorting rules in `selectMembers()` and test them.
- Do not call RuneLite `Client` from OkHttp callbacks.

## HiscoreService

File: `src/main/java/com/samwise0101/rivalry/HiscoreService.java`

`HiscoreService` fetches official OSRS hiscores for each roster player.

External components:

- RuneLite `HiscoreClient`.
- RuneLite `HiscoreEndpoint.NORMAL`.
- Injected `ScheduledExecutorService`.

Fetch behavior:

- Creates a fresh `ConcurrentHashMap` for each refresh.
- Starts one lookup per roster entry.
- Staggers lookups by 500 ms.
- Calls `hiscoreClient.lookupAsync(name, HiscoreEndpoint.NORMAL)`.
- Stores successful results as `PlayerSnapshot`.
- Keys results by lowercase player name.
- Logs failed individual lookups at debug.
- Completes the overall future after every lookup has finished.

Failure model:

- A single failed lookup does not fail the whole refresh.
- Missing players are absent from the stats map for that cycle.
- The calculator treats missing stats as unranked/unavailable.

Change guidance:

- Keep result maps fresh per refresh.
- Use `CompletableFuture.allOf()` for batch completion.
- If adding endpoints, make endpoint choice explicit and test data mapping.

