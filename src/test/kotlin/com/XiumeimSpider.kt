package com


import cn.mmooo.vertx.scrapy.*
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.AsyncFile
import io.vertx.ext.web.client.HttpResponse
import io.vertx.kotlin.core.file.openOptionsOf
import io.vertx.kotlin.core.json.jsonObjectOf
import org.jsoup.Jsoup
import java.net.URL
import javax.xml.ws.Response

fun main() {

    val startUrls = listOf(

            "http://www.xiumeim.com/albums/BOL.html",
            "http://www.xiumeim.com/albums/BoLoLIN.html",
            "http://www.xiumeim.com/albums/MF.html",
            "http://www.xiumeim.com/albums/Kimoe.html",
            "http://www.xiumeim.com/albums/UG.html",
            "http://www.xiumeim.com/albums/UGirls.html",
            "http://www.xiumeim.com/albums/LUGirls.html",
            "http://www.xiumeim.com/albums/Goddess.html",
            "http://www.xiumeim.com/albums/MiiTao.html",
            "http://www.xiumeim.com/albums/YOUMI.html",
            "http://www.xiumeim.com/albums/CANDY.html",
            "http://www.xiumeim.com/albums/HuaYan.html",
            "http://www.xiumeim.com/albums/MICAT.html",
            "http://www.xiumeim.com/albums/DKGirl.html",
            "http://www.xiumeim.com/albums/LeYuan.html",
            "http://www.xiumeim.com/albums/MTMENG.html",
            "http://www.xiumeim.com/albums/HuaYang.html",
            "http://www.xiumeim.com/albums/XINGYAN.html",
            "http://www.xiumeim.com/albums/XiuRen.html",
            "http://www.xiumeim.com/albums/MyGirl.html",
            "http://www.xiumeim.com/albums/BoLoli.html",
            "http://www.xiumeim.com/albums/Tukmo.html",
            "http://www.xiumeim.com/albums/MiStar.html",
            "http://www.xiumeim.com/albums/imiss.html",
            "http://www.xiumeim.com/albums/FeiLin.html",
            "http://www.xiumeim.com/albums/UXING.html",
            "http://www.xiumeim.com/albums/MFStar.html",
            "http://www.xiumeim.com/albums/YouWu.html",
            "http://www.xiumeim.com/albums/Taste.html",
            "http://www.xiumeim.com/albums/MintYe.html",
            "http://www.xiumeim.com/albums/WingS.html",
            "http://www.xiumeim.com/albums/Vedio.html",
            "http://www.xiumeim.com/albums/BOLOLITV.html",
            "http://www.xiumeim.com/albums/FEL_V.html",
            "http://www.xiumeim.com/albums/MIST_V.html",
            "http://www.xiumeim.com/albums/DK_V.html",
            "http://www.xiumeim.com/albums/IMS_V.html",
            "http://www.xiumeim.com/albums/mix.html",
            "http://www.xiumeim.com/albums/TuiGirl.html",
            "http://www.xiumeim.com/albums/TGod.html",
            "http://www.xiumeim.com/albums/QingDouKe.html",
            "http://www.xiumeim.com/albums/GIRLT.html",
            "http://www.xiumeim.com/albums/TouTiao.html",
            "http://www.xiumeim.com/albums/AISS.html",
            "http://www.xiumeim.com/albums/Legbaby.html",
            "http://www.xiumeim.com/albums/DDY.html",
            "http://www.xiumeim.com/albums/51MoDo.html",
            "http://www.xiumeim.com/albums/MISSLEG.html",
            "http://www.xiumeim.com/albums/vgirl.html",
            "http://www.xiumeim.com/albums/rihan.html",
            "http://www.xiumeim.com/albums/HeJi.html",
            "http://www.xiumeim.com/albums/TuiGirl.html",
            "http://www.xiumeim.com/albums/TuiGirl-3.html",
            "http://www.xiumeim.com/albums/TuiGirl-4.html",
            "http://www.xiumeim.com/albums/TuiGirl-2.html"
    );
    // 初始一个 request 对象

    val requests = startUrls.map {
        Request(
                url = URL(it),
                paeser = ::parseListPage //parser 回调
        )
    }.toTypedArray()
    val vertxSpiderOptions = VertxSpiderOptions(
            concurrentSize = 500, // 并发数 2000
            delayMs = 1,
            pipeline = Json2FilePipeline("xiumeim${System.currentTimeMillis()}.json")
    )
    deployVertxSpider(*requests, options = vertxSpiderOptions)

}

// 爬下来的页面响应
fun parseListPage(resp: HttpResponse<Buffer>, request: Request): Sequence<CrawlData> = sequence {
    logger.debug("这里解析页面")
    val document = Jsoup.parse(resp.bodyAsString(request.charset))
    document.select("a")
            .map { it.attr("href") }
            .filter { it.contains("/photos/") }
            .forEach {
                yield(Request(request.urlJoin(it), paeser = ::parseListPage))
            }

    val links = document.select("img.photosImg").map { it.attr("src") }
    if (links.isNotEmpty()) {
        val title = document.title()
        val obj = jsonObjectOf("images" to links, "title" to title)
        yield(Item(obj))
    }

}


