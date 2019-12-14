package com.tencent.careers

import cn.mmooo.vertx.scrapy.*
import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.vertx.core.buffer.*
import io.vertx.core.json.*
import io.vertx.ext.web.client.*
import java.net.*

private val mapper: ObjectMapper =
        jacksonObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

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
    println(resp.bodyAsJsonObject())
    val jobs = mapper
            .readValue<HRData>(resp.bodyAsString())
            .data?.posts
    jobs?.forEach {
        println(it)
        yield(Item(JsonObject(Json.encode(it)))) // 这里返回item 会被设置好的 Json2FilePipeline 处理
    }

}


data class HRData(
        @get:JsonProperty("Code") @field:JsonProperty("Code")
        val code: Long? = null,

        @get:JsonProperty("Data") @field:JsonProperty("Data")
        val data: Data? = null
)

data class Data(
        @get:JsonProperty("Count") @field:JsonProperty("Count")
        val count: Long? = null,

        @get:JsonProperty("Posts") @field:JsonProperty("Posts")
        val posts: List<Post>? = null
)

data class Post(
        @get:JsonProperty("Id") @field:JsonProperty("Id")
        val id: Long? = null,

        @get:JsonProperty("PostId") @field:JsonProperty("PostId")
        val postID: String? = null,

        @get:JsonProperty("RecruitPostId") @field:JsonProperty("RecruitPostId")
        val recruitPostID: Long? = null,

        @get:JsonProperty("RecruitPostName") @field:JsonProperty("RecruitPostName")
        val recruitPostName: String? = null,

        @get:JsonProperty("CountryName") @field:JsonProperty("CountryName")
        val countryName: String? = null,

        @get:JsonProperty("LocationName") @field:JsonProperty("LocationName")
        val locationName: String? = null,

        @get:JsonProperty("BGName") @field:JsonProperty("BGName")
        val bgName: String? = null,

        @get:JsonProperty("ProductName") @field:JsonProperty("ProductName")
        val productName: String? = null,

        @get:JsonProperty("CategoryName") @field:JsonProperty("CategoryName")
        val categoryName: String? = null,

        @get:JsonProperty("Responsibility") @field:JsonProperty("Responsibility")
        val responsibility: String? = null,

        @get:JsonProperty("LastUpdateTime") @field:JsonProperty("LastUpdateTime")
        val lastUpdateTime: String? = null,

        @get:JsonProperty("PostURL") @field:JsonProperty("PostURL")
        val postURL: String? = null,

        @get:JsonProperty("SourceID") @field:JsonProperty("SourceID")
        val sourceID: Long? = null,

        @get:JsonProperty("IsCollect") @field:JsonProperty("IsCollect")
        val isCollect: Boolean? = null,

        @get:JsonProperty("IsValid") @field:JsonProperty("IsValid")
        val isValid: Boolean? = null
)


