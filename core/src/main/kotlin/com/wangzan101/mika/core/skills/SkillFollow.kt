package com.wangzan101.mika.core.skills

import com.wangzan101.mika.core.knowledge.SiteMatch
import com.wangzan101.mika.core.knowledge.SkillPlaces
import com.wangzan101.mika.core.net.UrlHost
import com.wangzan101.mika.core.observation.PlaceKind
import com.wangzan101.mika.core.record.RunEvent
import com.wangzan101.mika.core.tool.ToolIndex

/**
 * 这次运行照着做了哪几份个人技能。用 `skill_read` 读过只说明看过,结果要记到真照着做了的技能上(#569):
 *
 * 1. 读完之后成功动过手(点、输入、打开 App、开网页、跑命令……,见 [ACTION_VERBS]);读完就回答或放弃的不算。
 * 2. 技能头部写了 App 或网站时,读完之后进过其中一个(读到过它的界面或页面);没写的只看第 1 条。
 *
 * 都按运行记录里实际发生的算,不要模型自己申报。宁可少认:没认出来只是这次不记,认错了会让好技能被停用。
 */
object SkillFollow {
    /** [places] 给出一份技能写明的 App 和网站;不认识这份技能时为 null,按没写算。 */
    fun followed(events: List<RunEvent>, used: List<RunEvent.SkillUsed>, tools: ToolIndex, places: (String) -> SkillPlaces?): Set<String> =
        used.groupBy { it.name }.filter { (name, reads) ->
            val readAt = reads.minOf { it.atMs }
            actedAfter(events, readAt, tools) && visitedAfter(events, readAt, places(name) ?: SkillPlaces())
        }.keys

    private fun actedAfter(events: List<RunEvent>, at: Long, tools: ToolIndex): Boolean = events.any {
        it is RunEvent.ToolResult && it.atMs > at && tools.verb(it.tool) in ACTION_VERBS && !it.resultJson.contains("\"ok\":false")
    }

    private fun visitedAfter(events: List<RunEvent>, at: Long, places: SkillPlaces): Boolean {
        if (!places.declared) return true
        return events.any { e ->
            if (e !is RunEvent.Observed || e.atMs <= at) return@any false
            val place = e.place ?: return@any false
            when (place.kind) {
                PlaceKind.App -> place.name in places.packages
                PlaceKind.Site -> SiteMatch.score(place.name, places.domains.toList()) != null
                PlaceKind.Page -> UrlHost.of(place.name)?.let { SiteMatch.score(it, places.domains.toList()) } != null
                else -> false
            }
        }
    }
}
