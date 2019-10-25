package cn.mmooo.vertx.scrapy

import io.vertx.core.*
import io.vertx.core.buffer.*
import io.vertx.core.file.*
import io.vertx.kotlin.coroutines.*
import kotlinx.coroutines.sync.*


class Json2FilePipeline(private val fileName: String) : Pipeline {

    private lateinit var file: AsyncFile

    private val mutex = Mutex()
    override suspend fun process(item: Item) {
        mutex.withLock {
            file.writePos -= 1
            file.write(item.data.toBuffer().appendString(",\n]")).await()
        }
    }

    override fun close() {
        file.close()
    }

    override fun open(vertx: Vertx) {
        file = vertx.fileSystem().openBlocking(fileName, OpenOptions().setWrite(true).setSync(true))
        file.write(Buffer.buffer("[\n]"))
    }

}

