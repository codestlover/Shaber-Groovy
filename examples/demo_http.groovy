// Run: cd .. && groovy -cp src/main/groovy examples/demo_http.groovy
// Hits https://shaber.sherolld.com.

import shaber.ShaberClient

def c = new ShaberClient()

println '--- health ---'
def h = c.health()
println "ok=${h.ok}  uptime=${h.uptimeSeconds}s"

println '\n--- stats ---'
def s = c.stats()
println "totalUploads=${s.totalUploads}  totalUsers=${s.totalUsers}  dayUsers=${s.dayUsers}"

println '\n--- wiki: random (en) ---'
def r = c.wikiRandom('en')
println "#${r.pageid}  ${r.title}"

println '\n--- wiki: Creature page (html len) ---'
def p = c.wikiPage('en', 'Creature', 'html')
println "html bytes: ${(p.html ?: '').size()}"

println '\n--- manifest endpoint count ---'
println "endpoints: ${c.manifest().endpoints.size()}"

println '\n--- user: MaxisDangerousYams ---'
try {
    def u = c.user('MaxisDangerousYams')
    println "id=${u.id}  tagline=\"${u.tagline}\""
} catch (e) {
    println "skipped: ${e.message}"
}
