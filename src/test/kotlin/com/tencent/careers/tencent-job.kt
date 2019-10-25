package com.tencent.careers

import cn.mmooo.vertx.scrapy.*
import io.vertx.core.buffer.*
import io.vertx.core.json.*
import io.vertx.ext.web.client.*
import java.net.*

fun main() {
    val links =
            Array(100) {
                """https://careers.tencent.com/tencentcareer/api/post/Query
                    ?pageIndex=$it
                    &pageSize=100
                    &language=zh-cn
                    &area=cn
                    """.replace("\\s+".let(::Regex), "")
            }
    val requests =
            links.map {
                Request(
                        URL(it),
                        parser = ::parseBody  // 回调函数
                )
            }.toTypedArray()
    // 一次放进100个 Request 对象
    deployVertxSpider(*requests, options = VertxSpiderOptions(
            concurrentSize = 10,
            delayMs = 500,
            pipeline = Json2FilePipeline("jobs.json") // 这里设置了处理item的对象
    ))

}


fun parseBody(resp: HttpResponse<Buffer>, req: Request) = sequence<CrawlData> {
    // 响应体是个 json
    val jobs: JsonArray? = resp
            .bodyAsJsonObject().getJsonObject("Data").getJsonArray("Posts")
    jobs?.forEach {
        if (it is JsonObject) {
            yield(Item(it)) // 这里返回item 会被设置好的 Json2FilePipeline 处理
        }
    }

}