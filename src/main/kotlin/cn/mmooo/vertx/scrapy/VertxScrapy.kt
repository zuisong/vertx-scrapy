package cn.mmooo.vertx.scrapy

import io.vertx.circuitbreaker.*
import io.vertx.core.*
import io.vertx.core.buffer.*
import io.vertx.core.http.*
import io.vertx.core.impl.*
import io.vertx.core.json.*
import io.vertx.ext.web.client.*
import io.vertx.kotlin.coroutines.*
import kotlinx.coroutines.*
import org.slf4j.*
import java.lang.invoke.*
import java.net.*
import java.util.*
import java.util.concurrent.*

val logger: Logger
    inline get() =  LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

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
    suspend fun push(request: Request)
    suspend fun pop(): Request?
}

/**
 * 本机内存里的request容器
 */
class LocalRequestHolder(
        private val deque: Deque<Request> = ConcurrentLinkedDeque<Request>()
) : RequestHolder {


    override suspend fun push(request: Request) {
        deque.addLast(request)
    }

    override suspend fun pop(): Request? = deque.pollFirst()
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

    vertx.deployVerticle(VertxScrapyVerticle(req.toList(), options), DeploymentOptions()
            .setWorker(options.isWorkerMode))
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
        val parser: Parser = ::defaultParser,

        val shutDownTimeOut: Int = Int.MAX_VALUE
) : CrawlData()

class VertxScrapyVerticle(
        private val startURL: Collection<Request>,
        private val options: VertxSpiderOptions = VertxSpiderOptions()
) : CoroutineVerticle() {
    /**
     * 保存待 爬取 的链接
     */
    private val requests: RequestHolder
            by lazy {
                LocalRequestHolder()
            }


    private val circuitBreaker: CircuitBreaker
            by lazy {
                CircuitBreaker
                        .create("httpclient", vertx, with(CircuitBreakerOptions()) {
                            maxFailures = 5
                            maxRetries = 5
                            timeout = 10_000
                            resetTimeout = 5000
                            this
                        }
                        )
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

    private var lastCrawledTime = System.currentTimeMillis()

    lateinit var webClient: WebClient
    private suspend fun runSpider() {
        while (true) {
            val req: Request? = requests.pop()
            if (req == null) {

                if (System.currentTimeMillis() - lastCrawledTime > 5 * 5000) {
                    logger.warn("all requests has been crawled! exit!")
                    stop()
                    lastCrawledTime = System.currentTimeMillis()
                }

                logger.warn("no more Request to crawl !")
                delay(TimeUnit.SECONDS.toMillis(5))
                continue
            }
            if (doneRequest.contains(req)) continue
            doneRequest.add(req)


            val response = circuitBreaker.execute<HttpResponse<Buffer>> {
                launch {
                    val response = webClient.requestAbs(req.method, req.url.toString())
                            .apply {
                                req.headers.forEach { (k, v) ->
                                    putHeader(k, v)
                                }
                            }
                            .timeout(0)
                            .sendBuffer(Buffer.buffer(req.body)).await()
                    it.complete(response)
                }
            }.await()
            lastCrawledTime = System.currentTimeMillis()
            val body = response.bodyAsString()
            val hashCode = body.hashCode()
            logger.debug("url-> {}, hashCode -> {}", req.url, hashCode)
            if (doneContentHash.contains(hashCode)) {
                continue
            }
            doneContentHash.add(hashCode)
            val sequence: Sequence<CrawlData> = (req.parser)(response, req)

            sequence.forEach { crawlData ->
                when (crawlData) {
                    is Item -> {
                        if (!done.contains(crawlData.hashCode())) {
                            done.add(crawlData.hashCode())
                            options.pipeline.process(crawlData)
                        }
                    }
                    is Request -> {
                        crawlData.takeUnless(doneRequest::contains)?.let { requests.push(it) }
                    }
                }
            }




            if (options.delayMs > 0)
                delay(options.delayMs)
        }
    }

    override suspend fun start() {

        startURL.forEach {
            requests.push(it)
        }

        webClient = WebClient.create(vertx, with(WebClientOptions()) {
            isKeepAlive = false
            isTcpFastOpen = true
            isTcpKeepAlive = false
            connectTimeout = 0
            isReuseAddress = true
            this
        })
        options.pipeline.open(vertx)

        repeat(options.concurrentSize) {

            launch {
                runSpider()
            }
        }

    }

    override suspend fun stop() {
        options.pipeline.close()
        vertx.undeploy(deploymentID)
    }

}

private fun defaultParser(resp: HttpResponse<Buffer>, req: Request): Sequence<CrawlData> = sequence {
    logger.warn("没有指定任何解析器，使用系统默认解析器")
    yield(Item(JsonObject(mapOf("body" to resp.bodyAsString()))))
}

