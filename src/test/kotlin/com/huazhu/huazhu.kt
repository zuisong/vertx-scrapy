package com.huazhu

import cn.mmooo.vertx.scrapy.*
import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.vertx.core.buffer.*
import io.vertx.core.http.*
import io.vertx.core.json.*
import io.vertx.ext.web.client.*
import java.net.*


private val mapper: ObjectMapper =
        jacksonObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)


fun main() {
    // 初始一个 request 对象

    val httpRequest = List(1200) {
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
            delayMs = 10_000, // 每个并发爬完一个页面延时 1s
            pipeline = Json2FilePipeline("huazhu.json")
    )
    deployVertxSpider(*httpRequest.toTypedArray(), options = vertxSpiderOptions)
}

// 爬下来的页面响应
fun parseListPage(resp: HttpResponse<Buffer>, request: Request): Sequence<CrawlData> = sequence {
    mapper.readValue<HuaPage>(resp.bodyAsString())
            .goodsVOList
            ?.forEach {
                logger.info("{}, {}", request.body, it)
                yield(Item(JsonObject(io.vertx.core.json.Json.encode(it))))
            }
}

data class HuaPage(
        val result: Boolean? = null,
        val totalSize: Long? = null,
        val goodsVOList: List<GoodsVOList>? = null,
        val conditionVOList: List<ConditionVOList>? = null
)

data class ConditionVOList(
        val conditionName: String? = null,
        val conditionType: String? = null
)

data class GoodsVOList(
        val cost: Double? = null,

        @get:JsonProperty("goodsId") @field:JsonProperty("goodsId")
        val goodsID: Long? = null,

        val goodsName: String? = null,
        val goodsNo: String? = null,

        @get:JsonProperty("goodsPicUrl") @field:JsonProperty("goodsPicUrl")
        val goodsPicURL: String? = null,

        val goodsSn: String? = null,
        val marketEnable: String? = null,
        val mktprice: Double? = null,
        val salesNum: Long? = null,
        val sort: Long? = null,
        val status: String? = null,

        @get:JsonProperty("storeId") @field:JsonProperty("storeId")
        val storeID: Long? = null,

        val storeName: String? = null,

        @get:JsonProperty("topOrgId") @field:JsonProperty("topOrgId")
        val topOrgID: Long? = null,

        val unit: String? = null,

        @get:JsonProperty("storeCateId") @field:JsonProperty("storeCateId")
        val storeCateID: Long? = null
)
