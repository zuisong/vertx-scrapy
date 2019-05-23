package com.douban

import cn.mmooo.vertx.scrapy.*
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse
import io.vertx.kotlin.core.json.jsonObjectOf
import org.jsoup.Jsoup
import java.net.URL

fun main() {
    // 初始一个 request 对象
    val httpRequest = Request(
            URL("https://movie.douban.com/top250"),
            paeser = ::parseListPage //parser 回调
    )
    val vertxSpiderOptions = VertxSpiderOptions(
            concurrentSize = 5, // 并发数 5
            delayMs = 1000 // 每个并发爬完一个页面延时 1s
    )
    deployVertxSpider(httpRequest, options = vertxSpiderOptions)

}
// 爬下来的页面响应
fun parseListPage(resp: HttpResponse<Buffer>, request: Request): Sequence<CrawlData> = sequence {
    logger.debug("这里解析页面")
    val document = Jsoup.parse(resp.bodyAsString(request.charset))
    document.select("div.item")
            .map { it.text().trim() }
            .map { Item(jsonObjectOf("item" to it)) }
            .forEach {
                // yield Item 表示数据
                yield(it)
            }
    document.select("div.paginator>a")
            .map { request.urlJoin(it.attr("href")) }
            .map { Request(url = it, paeser = ::parseListPage) }
            .forEach {
                // yield Request 会接着触发新一轮的请求
                yield(it)
            }
}
