// Run: cd .. && groovy -cp src/main/groovy examples/demo_radio.groovy
// Plays the first two tracks (consumes binary into byte counters) and exits.

import shaber.ShaberRadio

def radio    = new ShaberRadio()
def state    = [played: 0, bytes: 0]
def target   = 2

radio.on('hello',  { println "catalog: ${it.count} tracks" })
     .on('state',  { println "mode: ${it.mode}" })
     .on('track',  {
         println "▶ #${String.format('%03d', it.index)} ${it.name}"
         state.bytes = 0
     })
     .on('binary', { byte[] data -> state.bytes += data.length })
     .on('end',    {
         state.played++
         println "  end of track (${state.bytes} bytes)"
         if (state.played >= target) {
             println 'done — closing'
             radio.close()
         } else {
             radio.next()
         }
     })
     .on('interrupt', { println '  interrupt' })
     .on('error',     { println "! ${it.message}" })

radio.connect()
radio.await(180)
