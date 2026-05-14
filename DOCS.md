# Shaber-Groovy docs

A walkthrough of every method on the Shaber Groovy client, grouped by what
they talk to on the server. Response shapes (JSON keys, types, what each
field means) live in the API docs at https://shaber.sherolld.com/docs. This
page covers the Groovy side: signatures, what they take, what they return.

## Setup

```groovy
import shaber.ShaberClient
def c = new ShaberClient()
```

Options map:

```groovy
new ShaberClient(
    userAgent: 'my-bot',  // defaults to 'shaber-groovy'
    timeout:   30,        // seconds; defaults to 30
)
```

Every method below returns plain Groovy `Map`/`List`/`String`/numeric data
(parsed via `JsonSlurperClassic`). Non-200 responses throw
`ShaberHttpException`, which carries `status`, `path`, and the first 200
chars of the body:

```groovy
import shaber.ShaberHttpException
try {
    c.user('does-not-exist')
} catch (ShaberHttpException e) {
    println "status=${e.status}  path=${e.path}"
}
```

## Meta

| Method | What it does |
|---|---|
| `c.manifest()` | The self-describing manifest at `/api`. List of every endpoint with method, path, summary, category. |
| `c.health()`   | Liveness check. `[ok: true, uptimeSeconds: ...]`. |

## Daily stats

| Method | What it does |
|---|---|
| `c.stats()` | One-shot snapshot of the Spore.com counters (uploads, users, ratings, ...). Cached upstream for 5 min. |

## Creatures

| Method | What it does |
|---|---|
| `c.creature(id)` | The full attribute sheet for a creature: stats, parts, tags, dimensions. |

## Assets

These cover every kind of asset (creatures, vehicles, buildings, adventures,
captains, sporecasts).

| Method | What it does |
|---|---|
| `c.asset(id)`                                       | Asset metadata: id, type, name, tagline, owner, ratings, tags. |
| `c.assetComments(id, start, len)`                   | Comment thread on an asset. |
| `c.assetDownload(id)`                               | The legacy XML payload (`xml_data`) needed to import an asset back into Spore. |
| `c.assetLineage(id)`                                | Parent/remix chain of an asset. |

## Users

| Method | What it does |
|---|---|
| `c.user(name)`                                      | User profile: id, tagline, image. |
| `c.userAssets(name, start, len)`                    | Assets the user uploaded. |
| `c.userSporecasts(name)`                            | Sporecasts the user owns. |
| `c.userAchievements(name, start, len)`              | Achievement history. |
| `c.userBuddies(name, start, len)`                   | Outgoing buddy list. |
| `c.userSubscribers(name, start, len)`               | Users subscribed to this one. |
| `c.userTrophies(name)`                              | Trophies / badges. |
| `c.userCaptain(name)`                               | The user's space-stage captain (if any). |
| `c.userStats(name)`                                 | Aggregate counts: uploads, downloads, subscribers, total ratings. |

## Sporecasts

| Method | What it does |
|---|---|
| `c.sporecastAssets(id, start, len)` | Every asset in a sporecast. |

## Search & catalog

| Method | What it does |
|---|---|
| `c.search(view, type, start, len)`  | Browse-style search. `view` is the sort (e.g. `'NEWEST'`, `'TOP_RATED'`), `type` filters by asset kind. |
| `c.searchText(q, type)`             | Full-text Sporepedia search for `q`. |
| `c.trending(range)`                 | Trending uploads. `range` is `'today'`, `'week'`, `'month'`, ... |
| `c.featuredAssets()`                | Maxis-featured assets. |
| `c.featuredSporecasts()`            | Maxis-featured sporecasts. |
| `c.tags()`                          | The site-wide tag cloud, sorted by use count. |

## Adventures & captains

| Method | What it does |
|---|---|
| `c.adventureLeaderboard(id, scope)`     | Leaderboard rows for an adventure. `scope` is `'global'` or `'friends'`. |
| `c.captain(assetId)`                    | Captain build for an asset id (the space-stage incarnation). |

## Wiki

The Spore Fandom MediaWiki proxied across ten languages. `lang` is a
two-letter code: `'en'`, `'de'`, `'es'`, `'fr'`, `'it'`, `'ja'`, `'pl'`,
`'pt'`, `'ru'`, `'zh'`.

| Method | What it does |
|---|---|
| `c.wikiSearch(lang, q, limit, offset)`         | Full-text wiki search. |
| `c.wikiPage(lang, title, format)`              | Fetch a page. `format` is `'html'`, `'wikitext'` or `'both'`. |
| `c.wikiRandom(lang)`                           | A random page. |
| `c.wikiCategory(lang, name, limit, cursor)`    | Members of a category. |
| `c.wikiRecent(lang, limit, cursor)`            | Recent edits. |
| `c.wikiPages(lang, limit, cursor)`             | All pages (paginated). |
| `c.wikiInfo(lang)`                             | Per-language wiki stats: page count, edits, users. |
| `c.wikiLanglinks(lang, title)`                 | Translations of a page in other languages. |
| `c.wikiCategories(lang, title)`                | Categories a page belongs to. |
| `c.wikiBacklinks(lang, title, limit, cursor)`  | Pages that link to this one. |
| `c.wikiEmbeddedIn(lang, title, limit, cursor)` | Pages that transclude this template. |
| `c.wikiImages(lang, limit, cursor)`            | Image files on the wiki. |
| `c.wikiFile(lang, name)`                       | Metadata + URL for a single image. |

Pagination knobs vary by endpoint: most accept `limit` + `cursor` (server
returns the next cursor in the response), a few use `start` + `len`.

## Radio

The `/api/radio` WebSocket lives in `shaber.ShaberRadio`:

```groovy
import shaber.ShaberRadio

def radio = new ShaberRadio()
radio.on('hello',  { println "catalog: ${it.count}" })
     .on('track',  { println it.name })
     .on('binary', { byte[] data -> audio.write(data) })
     .on('end',    { radio.next() })

radio.connect()
radio.await()
```

`radio.connect()` opens the socket at
`wss://shaber.sherolld.com/api/radio` and returns once the handshake
completes. `radio.await()` blocks until the socket closes (peer-initiated
or `radio.close()`).

### Event handlers

```groovy
radio.on(eventName, closure)
```

Events:

| Event | Payload | When |
|---|---|---|
| `hello`     | `Map` with `count`, `tracks` | Once per connection. `tracks` is the full catalog. |
| `state`     | `Map` with `mode`            | Mode change (`'order'` or `'shuffle'`). |
| `track`     | `Map` with `index`, `name`, `file`, `mime`, `bytes` | About to send a new track. |
| `binary`    | `byte[]`                     | An audio chunk. |
| `end`       | (text payload, content unused) | Current track fully delivered. |
| `interrupt` | (text payload)               | Track aborted because you sent `next`/`prev`/`=name`. |
| `error`     | `Map` with `message`         | Server-side or socket error. |

### Commands

| Method | Wire | Effect |
|---|---|---|
| `radio.next()`        | `next`     | Advance one track. |
| `radio.prev()`        | `prev`     | Back one track. |
| `radio.shuffle()`     | `shuffle`  | Toggle shuffle on. |
| `radio.order()`       | `order`    | Toggle shuffle off. |
| `radio.list()`        | `list`     | Re-send the `hello` catalog. |
| `radio.pick(q)`       | `=<q>`     | Jump to the first track whose filename contains `q`. |
| `radio.close()`       | --         | Graceful shutdown of the socket. |

> Handler closures run on the JDK WebSocket reader thread. Keep them
> light or hand work off to your own executor, otherwise the receive
> buffer backs up.

## Errors

`ShaberHttpException extends RuntimeException` has three fields:

```groovy
catch (ShaberHttpException e) {
    e.status    // int, e.g. 404
    e.path      // "/api/users/does-not-exist"
    e.body      // first 200 chars of the response body
}
```
