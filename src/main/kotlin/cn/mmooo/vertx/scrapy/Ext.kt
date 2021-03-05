package cn.mmooo.vertx.scrapy

import io.vertx.core.*
import io.vertx.core.buffer.*
import io.vertx.core.file.*
import io.vertx.kotlin.coroutines.*
import kotlinx.coroutines.sync.*
import kotlin.properties.*


class Json2FilePipeline(private val fileName: String) : Pipeline {

    private lateinit var file: AsyncFile
    private var fileClosed by Delegates.notNull<Boolean>()

    private val mutex = Mutex()
    override suspend fun process(item: Item) {
        mutex.withLock {
            file.writePos -= 1
            file.write(item.data.toBuffer().appendString(",\n]")).await()
        }
    }

    override suspend fun close() {
        mutex.withLock {
            if (!fileClosed) {
                file.close { }
                fileClosed = true
            }
        }
    }

    override fun open(vertx: Vertx) {
        file = vertx.fileSystem().openBlocking(fileName, OpenOptions().setWrite(true).setSync(true))
        fileClosed = false
        file.write(Buffer.buffer("[\n]"))
    }

}

