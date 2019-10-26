package info.kinterest.datastores.tests

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import java.lang.IllegalStateException

class ChannelListener<T>(val ch:ReceiveChannel<T>) {
    val log = KotlinLogging.logger {  }
    val values : MutableList<T> = mutableListOf()
    val mutex = Mutex(

    )
    init {
        GlobalScope.launch {
            for(v in ch) {
                log.trace { "received $v" }
                mutex.withLock { values.add(v) }
            }
            log.info { "listening done" }
        }
    }

    suspend fun expect(timeout:Long = 1000, f:(T)->Boolean) : T = withTimeout(timeout) {
        while (true) {
            mutex.withLock {
                val v =  values.firstOrNull(f)
                if(v!=null) {
                    values.remove(v)
                    return@withTimeout v!!
                }
            }

            delay(10)
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException()
    }

    fun close() {
        ch.cancel()
    }
}