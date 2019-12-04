package com.huazhu


import cn.mmooo.vertx.scrapy.*
import com.google.gson.*
import io.vertx.core.buffer.*
import io.vertx.core.http.*
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
            concurrentSize = 5, // 并发数 5
            delayMs = 0, // 每个并发爬完一个页面延时 1s
            pipeline = Json2FilePipeline("huazhu.json")
    )
    deployVertxSpider(*httpRequest.toTypedArray(), options = vertxSpiderOptions)
}

// 爬下来的页面响应
fun parseListPage(resp: HttpResponse<Buffer>, request: Request): Sequence<CrawlData> = sequence {
    val body = resp.bodyAsString(Charsets.UTF_8.name())
    val jsonObject = JsonParser.parseString(body).asJsonObject
    println(jsonObject)
    val jsonArray = jsonObject.getAsJsonArray("goodsVOList")
    println(request.body)
    jsonArray.toList()
            .filterIsInstance<JsonObject>()
            .forEach {
                println(it)
                yield(Item(io.vertx.core.json.JsonObject(it.toString())))
            }
}

