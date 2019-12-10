package com.huazhu


import cn.mmooo.vertx.scrapy.*
import io.vertx.core.buffer.*
import io.vertx.core.http.*
import io.vertx.core.json.*
import io.vertx.ext.web.client.*
import java.net.*

fun main() {
    // 初始一个 request 对象

    val httpRequest =
            List(1200) {
                Request(
                        URL("https://b2b-h5.huazhu.com/search/goodsListJson"),
                        method = HttpMethod.POST,
                        headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                        body = "pageNo=${it}",
                        parser = ::parseListPage //parser 回调
                )
            }
    val vertxSpiderOptions = VertxSpiderOptions(
            concurrentSize = 50, // 并发数 5
            delayMs = 0, // 每个并发爬完一个页面延时 1s
            pipeline = Json2FilePipeline("huazhu.json")
    )
    deployVertxSpider(*httpRequest.toTypedArray(), options = vertxSpiderOptions)
}

// 爬下来的页面响应
fun parseListPage(resp: HttpResponse<Buffer>, request: Request): Sequence<CrawlData> = sequence {
    val jsonObject = resp.bodyAsJsonObject()
    logger.info(request.body)
    val jsonArray = jsonObject.getJsonArray("goodsVOList")
    jsonArray
            .filterIsInstance<JsonObject>()
            .forEach {
                logger.info("{}", it)
                yield(Item(JsonObject(it.toString())))
            }
}

