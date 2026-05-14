<p align="center">
  <img src="./logo.webp" alt="Shaber" width="320">
</p>

# Shaber-Groovy

Groovy bindings for the Shaber API. Shaber is a JSON re-host of the
Spore.com archive: it pulls Spore's creaky XML/HTML endpoints and
re-emits them as clean, pretty-printed JSON, exposes the Sporepedia
catalog that the official site only hands out over DWR/AJAX, proxies the
Spore Fandom wiki across ten languages, and live-streams the OST as Opus
over WebSocket. This package wraps the whole surface so you get plain
Groovy `Map`/`List` data without writing HTTP, JSON or WebSocket glue.

Handy for:

- pulling Spore data into JVM-based archivers, scrapers or bots
- wiring Shaber into a bigger Gradle / Spring / Micronaut app
- ad-hoc Groovy shell scripts and Jenkins jobs
- routing the OST stream into a `javax.sound` player or Discord bot

API:      https://shaber.sherolld.com
API DOCS: https://shaber.sherolld.com/docs

Want a client in a different language? Roll your own from
https://shaber.sherolld.com/docs.

Needs JDK 11+ and Groovy 3.x or 4.x. No third-party HTTP or WebSocket libs;
it rides on `java.net.http.HttpClient` and `java.net.http.WebSocket`.

## Install

If you already have Gradle:

```bash
gradle build
gradle publishToMavenLocal     # io.shaber:shaber-groovy:0.0.0
```

Then:

```groovy
dependencies {
    implementation 'io.shaber:shaber-groovy:0.0.0'
}
```

For one-off scripts, no build step is needed:

```bash
groovy -cp src/main/groovy examples/demo_http.groovy
```

## Use it

```groovy
import shaber.ShaberClient

def c = new ShaberClient()

println c.health().uptimeSeconds
println c.stats().totalUsers
println c.wikiRandom('en').title
```

Every `/api/*` endpoint has a matching method (~40 in total). Responses are
parsed via `JsonSlurperClassic` so you get plain `Map`/`List` back. Non-200
replies throw `ShaberHttpException`, which carries `status`, `path`, and the
first 200 chars of the body.

If you're hitting big endpoints, bump the timeout:

```groovy
def c = new ShaberClient(timeout: 60)
```

Full method reference, parameter docs and event payloads: [DOCS.md](./DOCS.md).

## Radio

```groovy
import shaber.ShaberRadio

def radio = new ShaberRadio()
def audio = new FileOutputStream('out.opus')

radio.on('hello',  { println "${it.count} tracks" })
     .on('track',  { println it.name })
     .on('binary', { byte[] data -> audio.write(data) })
     .on('end',    { radio.next() })

radio.connect()
radio.await()
audio.close()
```

Commands: `next`, `prev`, `shuffle`, `order`, `pick(q)`, `list`, `close`.
Events: `hello`, `state`, `track`, `binary`, `end`, `interrupt`, `error`.

Handler closures run on the JDK WebSocket reader thread. Keep them light or
hand work off to your own executor, otherwise the receive buffer backs up.

---

BSD-3-Clause.
