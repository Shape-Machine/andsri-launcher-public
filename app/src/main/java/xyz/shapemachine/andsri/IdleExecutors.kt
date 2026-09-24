package xyz.shapemachine.andsri

import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Serial work with no resident worker after 15 seconds idle. */
internal object IdleExecutors {
    fun single(): ExecutorService = ThreadPoolExecutor(0, 1, 15, TimeUnit.SECONDS, LinkedBlockingQueue())
}
