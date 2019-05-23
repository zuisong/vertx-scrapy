# Vert.x Scrapy 
基于 [Vert.x](https://vertx.io) 和 [kotlin](https://kotlinlang.org) 写的一个 爬虫框架  
模仿 python 的明星爬虫框架 Scrapy
---
借助 Vert.x 优秀的线程模型，可以用很少的native thread 实现高并发请求


Todo
- 借助 `数据库` 或者 `redis` 实现 分布式爬虫 
- 实现可暂停功能
- 完善文档
- 自带一个解析器

爬取豆瓣的实例
```kotlin
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

```