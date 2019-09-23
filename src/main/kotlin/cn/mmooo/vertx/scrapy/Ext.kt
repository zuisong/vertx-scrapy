package cn.mmooo.vertx.scrapy

import io.vertx.core.*
import io.vertx.core.buffer.*
import io.vertx.core.file.*


class Json2FilePipeline(private val fileName: String) : Pipeline {

    private lateinit var file: AsyncFile

    override suspend fun process(item: Item) {
        synchronized(this) {
            file.writePos -= 1
            file.write(item.data.toBuffer().appendString(",\n]"))
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

