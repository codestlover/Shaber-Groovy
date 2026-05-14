package shaber

import groovy.json.JsonSlurperClassic
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Thrown when the Shaber server replies with a non-200 status.
 */
class ShaberHttpException extends RuntimeException {
    final int    status
    final String path
    final String body

    ShaberHttpException(int status, String path, String body) {
        super("Shaber HTTP ${status} on ${path}: ${(body ?: '').take(200)}")
        this.status = status
        this.path   = path
        this.body   = body ?: ''
    }
}

/**
 * Shaber HTTP client.
 *
 * <pre>
 *   def c = new ShaberClient()
 *   println c.health().uptimeSeconds
 * </pre>
 *
 * Responses come back as plain {@code Map}/{@code List} (parsed via
 * {@link groovy.json.JsonSlurperClassic}). Non-200 throws
 * {@link ShaberHttpException}.
 *
 * Needs JDK 11+.
 */
class ShaberClient {
    String     baseUrl
    String     userAgent
    HttpClient client
    // JsonSlurperClassic — JsonSlurper's lazy parser uses a ServiceLoader for
    // FastString that fails on newer JDKs (e.g. JDK 25). Classic mode is
    // fast enough for our payloads.
    JsonSlurperClassic slurper = new JsonSlurperClassic()

    /**
     * Options map keys: baseUrl, userAgent, timeout (seconds).
     */
    ShaberClient(Map opts = [:]) {
        baseUrl   = opts.baseUrl   ?: 'https://shaber.sherolld.com'
        userAgent = opts.userAgent ?: 'shaber-groovy'
        int timeout = (opts.timeout ?: 30) as int
        client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(timeout))
            .build()
    }

    // ------------------------------------------------------------ low level

    private static String enc(Object s) {
        URLEncoder.encode(String.valueOf(s), 'UTF-8').replace('+', '%20')
    }

    private String buildUrl(String path, Map query) {
        if (!query) return baseUrl + path
        def parts = query.findAll { k, v -> v != null && v != '' }
                         .collect { k, v -> "${enc(k)}=${enc(v)}" }
        parts ? "${baseUrl}${path}?${parts.join('&')}" : baseUrl + path
    }

    /**
     * Issue a GET request. Returns the parsed JSON body (a Map or List).
     * Throws ShaberHttpException for non-200 responses.
     */
    def get(String path, Map query = null) {
        String url = buildUrl(path, query)
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .header('Accept', 'application/json')
            .header('User-Agent', userAgent)
            .GET()
            .build()
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 200) {
            throw new ShaberHttpException(resp.statusCode(), path, resp.body())
        }
        resp.body() ? slurper.parseText(resp.body()) : null
    }

    // ------------------------------------------------------------ meta

    /** GET /api — self-describing manifest. */
    def manifest() { get('/api') }
    /** GET /api/health — uptime + version. */
    def health()   { get('/api/health') }

    // ------------------------------------------------------------ legacy mirror

    /** GET /api/stats. */
    def stats() { get('/api/stats') }

    /** GET /api/creatures/:id. */
    def creature(id) { get("/api/creatures/${enc(id)}") }

    /** GET /api/assets/:id. */
    def asset(id) { get("/api/assets/${enc(id)}") }

    /** GET /api/assets/:id/comments?start&len. */
    def assetComments(id, int start = 0, int len = 10) {
        get("/api/assets/${enc(id)}/comments", [start: start, len: len])
    }

    /** GET /api/assets/:id/download. */
    def assetDownload(id) { get("/api/assets/${enc(id)}/download") }

    /** GET /api/assets/:id/lineage. */
    def assetLineage(id)  { get("/api/assets/${enc(id)}/lineage") }

    /** GET /api/users/:name. */
    def user(String name) { get("/api/users/${enc(name)}") }

    /** GET /api/users/:name/assets?start&len. */
    def userAssets(String name, int start = 0, int len = 10) {
        get("/api/users/${enc(name)}/assets", [start: start, len: len])
    }

    /** GET /api/users/:name/sporecasts. */
    def userSporecasts(String name) { get("/api/users/${enc(name)}/sporecasts") }

    /** GET /api/users/:name/achievements?start&len. */
    def userAchievements(String name, int start = 0, int len = 10) {
        get("/api/users/${enc(name)}/achievements", [start: start, len: len])
    }

    /** GET /api/users/:name/buddies?start&len. */
    def userBuddies(String name, int start = 0, int len = 10) {
        get("/api/users/${enc(name)}/buddies", [start: start, len: len])
    }

    /** GET /api/users/:name/subscribers?start&len. */
    def userSubscribers(String name, int start = 0, int len = 10) {
        get("/api/users/${enc(name)}/subscribers", [start: start, len: len])
    }

    /** GET /api/sporecasts/:id/assets?start&len. */
    def sporecastAssets(id, int start = 0, int len = 10) {
        get("/api/sporecasts/${enc(id)}/assets", [start: start, len: len])
    }

    /** GET /api/search?view&type&start&len. */
    def search(String view = 'TOP_RATED', String type = null,
               int start = 0, int len = 10) {
        get('/api/search', [view: view, type: type, start: start, len: len])
    }

    // ------------------------------------------------------------ sporepedia

    /** GET /api/search/text?q&type. */
    def searchText(String q, String type = null) {
        get('/api/search/text', [q: q, type: type])
    }

    /** GET /api/users/:name/trophies. */
    def userTrophies(String name) { get("/api/users/${enc(name)}/trophies") }

    /** GET /api/featured/assets. */
    def featuredAssets() { get('/api/featured/assets') }

    /** GET /api/featured/sporecasts. */
    def featuredSporecasts() { get('/api/featured/sporecasts') }

    /** GET /api/trending/:range — range ∈ day|week|month|all. */
    def trending(String range) { get("/api/trending/${enc(range)}") }

    /** GET /api/adventures/:id/leaderboard?scope. */
    def adventureLeaderboard(id, String scope = 'world') {
        get("/api/adventures/${enc(id)}/leaderboard", [scope: scope])
    }

    /** GET /api/captains/:assetId. */
    def captain(assetId) { get("/api/captains/${enc(assetId)}") }

    /** GET /api/users/:name/captain. */
    def userCaptain(String name) { get("/api/users/${enc(name)}/captain") }

    /** GET /api/users/:name/stats. */
    def userStats(String name) { get("/api/users/${enc(name)}/stats") }

    /** GET /api/tags. */
    def tags() { get('/api/tags') }

    // ------------------------------------------------------------ wiki

    /** GET /api/wiki/:lang/search?q&limit&offset. */
    def wikiSearch(String lang, String q, int limit = 10, int offset = 0) {
        get("/api/wiki/${lang}/search", [q: q, limit: limit, offset: offset])
    }

    /** GET /api/wiki/:lang/page/:title?format. */
    def wikiPage(String lang, String title, String format = 'both') {
        get("/api/wiki/${lang}/page/${enc(title)}", [format: format])
    }

    /** GET /api/wiki/:lang/random. */
    def wikiRandom(String lang) { get("/api/wiki/${lang}/random") }

    /** GET /api/wiki/:lang/category/:name?limit&cursor. */
    def wikiCategory(String lang, String name, int limit = 10, String cursor = null) {
        get("/api/wiki/${lang}/category/${enc(name)}", [limit: limit, cursor: cursor])
    }

    /** GET /api/wiki/:lang/recent?limit&cursor. */
    def wikiRecent(String lang, int limit = 10, String cursor = null) {
        get("/api/wiki/${lang}/recent", [limit: limit, cursor: cursor])
    }

    /** GET /api/wiki/:lang/pages?limit&cursor. */
    def wikiPages(String lang, int limit = 20, String cursor = null) {
        get("/api/wiki/${lang}/pages", [limit: limit, cursor: cursor])
    }

    /** GET /api/wiki/:lang/info. */
    def wikiInfo(String lang) { get("/api/wiki/${lang}/info") }

    /** GET /api/wiki/:lang/page/:title/langlinks. */
    def wikiLanglinks(String lang, String title) {
        get("/api/wiki/${lang}/page/${enc(title)}/langlinks")
    }

    /** GET /api/wiki/:lang/page/:title/categories. */
    def wikiCategories(String lang, String title) {
        get("/api/wiki/${lang}/page/${enc(title)}/categories")
    }

    /** GET /api/wiki/:lang/page/:title/backlinks?limit&cursor. */
    def wikiBacklinks(String lang, String title, int limit = 10, String cursor = null) {
        get("/api/wiki/${lang}/page/${enc(title)}/backlinks", [limit: limit, cursor: cursor])
    }

    /** GET /api/wiki/:lang/page/:title/embeddedin?limit&cursor. */
    def wikiEmbeddedIn(String lang, String title, int limit = 10, String cursor = null) {
        get("/api/wiki/${lang}/page/${enc(title)}/embeddedin", [limit: limit, cursor: cursor])
    }

    /** GET /api/wiki/:lang/images?limit&cursor. */
    def wikiImages(String lang, int limit = 10, String cursor = null) {
        get("/api/wiki/${lang}/images", [limit: limit, cursor: cursor])
    }

    /** GET /api/wiki/:lang/file/:name. */
    def wikiFile(String lang, String name) {
        get("/api/wiki/${lang}/file/${enc(name)}")
    }
}
