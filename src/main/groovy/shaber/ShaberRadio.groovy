package shaber

import groovy.json.JsonSlurperClassic
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for Shaber's <code>/api/radio</code>.
 *
 * Uses the built-in {@link java.net.http.WebSocket}, no third-party deps.
 *
 * <pre>
 *   def radio = new ShaberRadio()
 *   radio.on('hello',  { println "catalog: ${it.count}" })
 *        .on('track',  { println it.name })
 *        .on('binary', { audioOut.write(it) })
 *        .on('end',    { radio.next() })
 *   radio.connect()
 *   radio.await()
 * </pre>
 *
 * Handlers run on the JDK WebSocket reader thread, so keep them light or
 * hand work off to your own executor.
 */
class ShaberRadio {

    private final Map<String, List<Closure>> handlers = [
        hello:     [], state:     [], track:     [],
        end:       [], interrupt: [], 'error':   [],
        binary:    [],
    ]

    // JsonSlurperClassic instead of JsonSlurper — the latter's FastString
    // ServiceLoader can fail on newer JDKs (e.g. JDK 25), and we don't need
    // its lazy/index-overlay perf for tiny WS control frames.
    private final JsonSlurperClassic slurper = new JsonSlurperClassic()
    private final StringBuilder textBuf      = new StringBuilder()
    private final CountDownLatch closedSignal = new CountDownLatch(1)

    private WebSocket ws

    /**
     * Register a handler for an event.
     *
     * Events: {@code hello}, {@code state}, {@code track}, {@code end},
     * {@code interrupt}, {@code error}, {@code binary}. Handlers receive the
     * parsed JSON map (for text events) or a {@code byte[]} (for {@code binary}).
     */
    ShaberRadio on(String event, Closure fn) {
        if (!handlers.containsKey(event)) {
            throw new IllegalArgumentException("unknown event: ${event} (events: ${handlers.keySet()})")
        }
        handlers[event] << fn
        this
    }

    private void dispatch(String event, payload) {
        handlers[event].each { fn ->
            try { fn.call(payload) } catch (Throwable t) {
                System.err.println("shaber.radio: handler for ${event} threw: ${t}")
            }
        }
    }

    /**
     * Open the WebSocket. Returns once the handshake completes; events start
     * arriving on the JDK reader thread immediately afterwards.
     *
     * Opens a WebSocket to wss://shaber.sherolld.com/api/radio.
     */
    void connect(Map opts = [:]) {
        String url = opts.url
        if (!url && opts.baseUrl) {
            String b = ((String) opts.baseUrl).replaceAll('/$', '')
            url = b.replaceFirst(/^https/, 'wss').replaceFirst(/^http/, 'ws') + '/api/radio'
        }
        if (!url) url = 'wss://shaber.sherolld.com/api/radio'
        def listener = new WebSocket.Listener() {

            // Groovy's anonymous-class instantiation of a Java interface
            // doesn't always inherit `Listener.onOpen`'s default body
            // (`socket.request(1)`). Without an initial request count the
            // WebSocket reader will silently sit idle, never calling onText.
            // Override explicitly.
            @Override
            void onOpen(WebSocket socket) {
                socket.request(1)
            }

            @Override
            CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                textBuf.append(data)
                if (last) {
                    String full = textBuf.toString()
                    textBuf.setLength(0)
                    def msg = null
                    try { msg = slurper.parseText(full) } catch (ignored) {}
                    if (msg instanceof Map) {
                        String type = msg.type as String
                        if (type && handlers.containsKey(type)) {
                            dispatch(type, msg)
                        }
                    }
                }
                socket.request(1)
                null
            }

            @Override
            CompletionStage onBinary(WebSocket socket, ByteBuffer buf, boolean last) {
                byte[] arr = new byte[buf.remaining()]
                buf.get(arr)
                dispatch('binary', arr)
                socket.request(1)
                null
            }

            @Override
            CompletionStage onClose(WebSocket socket, int code, String reason) {
                closedSignal.countDown()
                null
            }

            @Override
            void onError(WebSocket socket, Throwable t) {
                dispatch('error', [type: 'error', message: String.valueOf(t)])
                closedSignal.countDown()
            }
        }
        ws = HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .buildAsync(URI.create(url), listener)
            .join()
    }

    /** Send a raw text command. */
    void send(String cmd) {
        if (ws == null) throw new IllegalStateException("shaber.radio: not connected — call connect() first")
        ws.sendText(cmd, true).join()
    }

    void next()    { send('next') }
    void prev()    { send('prev') }
    void shuffle() { send('shuffle') }
    void order()   { send('order') }
    void list()    { send('list') }
    void pick(String query) { send('=' + query) }

    /** Gracefully close the connection. */
    void close() {
        try {
            ws?.sendClose(WebSocket.NORMAL_CLOSURE, 'bye')?.join()
        } catch (ignored) {
            // already closed — that's fine
        }
        closedSignal.countDown()
    }

    /**
     * Block until the peer closes or {@link #close()} is called.
     * Pass {@code timeoutSeconds > 0} to give up after a timeout.
     */
    void await(long timeoutSeconds = 0) {
        if (timeoutSeconds > 0) {
            closedSignal.await(timeoutSeconds, TimeUnit.SECONDS)
        } else {
            closedSignal.await()
        }
    }
}
