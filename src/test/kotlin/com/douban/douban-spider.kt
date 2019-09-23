package com.douban

import cn.mmooo.vertx.scrapy.*
import io.vertx.core.buffer.*
import io.vertx.core.json.*
import io.vertx.ext.web.client.*
import org.jsoup.*
import java.net.*

fun main() {
    // 初始一个 request 对象
    val httpRequest = Request(
            URL("https://movie.douban.com/top250"),
            parser = ::parseListPage //parser 回调
    )
    val vertxSpiderOptions = VertxSpiderOptions(
            concurrentSize = 1, // 并发数 5
            delayMs = 10, // 每个并发爬完一个页面延时 1s
            pipeline = Json2FilePipeline("douban.json")
    )
    deployVertxSpider(httpRequest, options = vertxSpiderOptions)

}

// 爬下来的页面响应
fun parseListPage(resp: HttpResponse<Buffer>, request: Request): Sequence<CrawlData> = sequence {
    logger.debug("这里解析页面")
    val html = resp.bodyAsString(request.charset)
    val document = Jsoup.parse(html)
    document.select("div.item")
            .map { it.text().trim() }
            .map { Item(JsonObject().put("item", it)) }
            .forEach {
                // yield Item 表示数据
                println(it)
                yield(it)
            }
    document.select("div.paginator>a")
            .map { request.urlJoin(it.attr("href")) }
            .map { Request(url = it, parser = ::parseListPage) }
            .forEach {
                // yield Request 会接着触发新一轮的请求
                yield(it)
            }
}
