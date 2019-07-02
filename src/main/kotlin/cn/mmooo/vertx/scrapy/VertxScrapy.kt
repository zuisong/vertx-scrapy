package cn.mmooo.vertx.scrapy

import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.impl.ConcurrentHashSet
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.circuitbreaker.circuitBreakerOptionsOf
import io.vertx.kotlin.circuitbreaker.executeCommandAwait
import io.vertx.kotlin.core.deploymentOptionsOf
import io.vertx.kotlin.core.executeBlockingAwait
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.kotlin.core.vertxOptionsOf
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.awaitBlocking
import io.vertx.kotlin.ext.web.client.sendBufferAwait
import io.vertx.kotlin.ext.web.client.webClientOptionsOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit

val logger: Logger = LoggerFactory.getLogger("vertx-scrapy")

/**
 * @author zuisong
 */
data class VertxSpiderOptions(
        val delayMs: Long = 5000,
        val concurrentSize: Int = 2,
        val isWorkerMode: Boolean = false,
        val pipeline: Pipeline = PrintPipeline()
)

/**
 * 保存request的容器
 */
interface RequestHolder {
    fun push(request: Request)
    fun pop(): Request?
}

/**
 * 本机内存里的request容器
 */
class LocalRequestHolder(
        private val deque: Deque<Request> = ConcurrentLinkedDeque<Request>()
) : RequestHolder {


    override fun push(request: Request) {
        deque.addLast(request)
    }

    override fun pop(): Request? = deque.pollFirst()
}

/**
 * 本机内存里的request容器
 */
class RedisRequestHolder(
) : RequestHolder {
    override fun push(request: Request) {
        TODO()
    }

    override fun pop(): Request? = TODO()
}

/**
 * 保存已经爬取的数据, 避免重新爬取
 */
interface DuplicationPredictor<T> {
    fun add(t: T)
    fun contains(t: T): Boolean
}

class SetDuplicationPredictor<T>(
        private val set: MutableSet<T> = ConcurrentHashSet()
) : DuplicationPredictor<T> {
    override fun add(t: T) {
        set.add(t)
    }

    override fun contains(t: T): Boolean = set.contains(t)
}


interface Pipeline {
    fun open(vertx: Vertx)
    suspend fun process(item: Item)
    fun close()
}

class PrintPipeline : Pipeline {
    override fun open(vertx: Vertx) {}

    override suspend fun process(item: Item) {
        println(item)
    }

    override fun close() {}
}

/**
 * 部署一个爬虫
 */
fun deployVertxSpider(
        vararg req: Request,
        options: VertxSpiderOptions = VertxSpiderOptions()) {
    val vertx = Vertx.vertx()

    vertx.deployVerticle(VertxScrapyVerticle(req.toList(), options), deploymentOptionsOf(
            worker = options.isWorkerMode
    ))
}

typealias Parser = (resp: HttpResponse<Buffer>, req: Request) -> Sequence<CrawlData>

sealed class CrawlData

/**
 * @see <a href="https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6519518">具体看这个bug</a>
 */
fun fixupRelative(baseURL: String, relative: String): String {
    var workingRelative = relative.trim()
    if (workingRelative.isNotEmpty() && (workingRelative[0] == '?' || workingRelative[0] == ';')) {
        val stripTo = workingRelative[0]
        var stripToLoc = baseURL.indexOf(stripTo)
        if (0 > stripToLoc && ';' == stripTo) {
            stripToLoc = baseURL.indexOf('?')  // if relative and has a ';' but the baseURL does not, then we will strip to ?
            if (0 > stripToLoc) {
                stripToLoc = baseURL.length
            }
        }
        if (-1 == stripToLoc) {
            stripToLoc = baseURL.length
        }
        val startLoc = baseURL.lastIndexOf('/', stripToLoc) + 1
        if (startLoc in 1..stripToLoc) {  // only enter if we have good values, otherwise give up and hope for the best
            val prefix = baseURL.substring(startLoc, stripToLoc)
            workingRelative = prefix + workingRelative
        }
    }
    return workingRelative
}

data class Item(val data: JsonObject) : CrawlData() {
    override fun toString(): String {
        return data.toString()
    }
}

fun Request.urlJoin(str: String): URL = URL(url, fixupRelative(url.toString(), str))

data class Request(
        /**
         * 请求链接
         */
        val url: URL,
        /**
         * 请求方法  默认 get
         */
        val method: HttpMethod = HttpMethod.GET,
        /**
         * 请求头
         */
        val headers: Map<String, String> = emptyMap(),
        /**
         * 请求体
         */
        val body: String = "",
        /**
         * 元数据  可以用来传到 parse里, 跨parser传数据用
         */
        val metaData: JsonObject = JsonObject(),
        /**
         * 解析body时的编码
         */
        val charset: String = Charsets.UTF_8.name(),
        /**
         * 解析器, 解析 response 时用
         */
        val paeser: Parser = ::defaultParser
) : CrawlData()

class VertxScrapyVerticle(
        startURL: Collection<Request>,
        private val options: VertxSpiderOptions = VertxSpiderOptions()
) : CoroutineVerticle() {
    /**
     * 保存待 爬取 的链接
     */
    private val requests: RequestHolder = LocalRequestHolder()

    init {
        startURL.forEach(requests::push)
    }

    /**
     * 已爬取的的请求
     */
    private val doneRequest: DuplicationPredictor<Request> = SetDuplicationPredictor()
    /**
     * 已经爬取的响应内容的 hashCode
     */
    private val doneContentHash: DuplicationPredictor<Int> = SetDuplicationPredictor()
    /**
     * 已经爬取的 item 的 hashCode
     */
    private val done = ConcurrentHashSet<Int>()

    lateinit var webClient: WebClient
    private suspend fun runSpider() {
        while (true) {
            val req: Request? = requests.pop()
            if (req == null) {
                logger.warn("no more Request to crawl !")
                delay(TimeUnit.SECONDS.toMillis(5))
                continue
            }
            if (doneRequest.contains(req)) continue
            doneRequest.add(req)
            val circuitBreaker = CircuitBreaker
                    .create("httpclient", vertx, circuitBreakerOptionsOf(
                            maxFailures = 5,
                            maxRetries = 5,
                            timeout = 10_000,
                            resetTimeout = 5000
                    ))


            val response = circuitBreaker.executeCommandAwait<HttpResponse<Buffer>> {
                launch {
                    val response = webClient.requestAbs(req.method, req.url.toString())
                            .apply {
                                req.headers.forEach { (k, v) ->
                                    putHeader(k, v)
                                }
                            }
                            .timeout(0)
                            .sendBufferAwait(Buffer.buffer(req.body))
                    it.complete(response)
                }
            }
            val body = response.bodyAsString()
            val hashCode = body.hashCode()
            logger.debug("url-> {}, hashCode -> {}", req.url, hashCode)
            if (doneContentHash.contains(hashCode)) {
                continue
            }
            doneContentHash.add(hashCode)
            val sequence: Sequence<CrawlData> = vertx.executeBlockingAwait<Sequence<CrawlData>>({ (req.paeser)(response, req) }, true)!!

            sequence.forEach {
                when (it) {
                    is Item -> {
                        if (!done.contains(it.hashCode())) {
                            done.add(it.hashCode())
                            options.pipeline.process(it)
                        }
                    }
                    is Request -> {
                        it.takeUnless(doneRequest::contains)?.let(requests::push)
                    }
                }
            }

            if (options.delayMs > 0)
                delay(options.delayMs)
        }
    }

    override suspend fun start() {
        webClient = WebClient.create(vertx, webClientOptionsOf(
                keepAlive = false,
                tcpFastOpen = true,
                tcpKeepAlive = false,
                connectTimeout = 0,
                reuseAddress = true
        ))
        options.pipeline.open(vertx)

        repeat(options.concurrentSize) {
            launch {
                runSpider()
            }
        }

    }

    override suspend fun stop() {
        options.pipeline.close()
        super.stop()
    }

}

private fun defaultParser(resp: HttpResponse<Buffer>, req: Request): Sequence<CrawlData> = sequence {
    logger.warn("没有指定任何解析器，使用系统默认解析器")
    yield(Item(jsonObjectOf("body" to resp.bodyAsString())))
}

