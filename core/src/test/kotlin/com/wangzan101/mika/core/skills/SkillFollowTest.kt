package com.wangzan101.mika.core.skills

import com.wangzan101.mika.core.assembly.knownTools
import com.wangzan101.mika.core.knowledge.GuideCatalog
import com.wangzan101.mika.core.knowledge.SkillPlaces
import com.wangzan101.mika.core.observation.Place
import com.wangzan101.mika.core.record.RunEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 照着做了才算:读完之后动过手,写了 App / 网站的还要读完之后进过其中一个(#569)。 */
class SkillFollowTest {
    private val tools = knownTools()
    private fun read(name: String, at: Long) = RunEvent.SkillUsed(at, name, "viewed")
    private fun act(at: Long, tool: String = "phone_tap", ok: Boolean = true) =
        RunEvent.ToolResult(at, 1, tool, if (ok) """{"ok":true}""" else """{"ok":false}""", ok, null)
    private fun saw(at: Long, place: Place) = RunEvent.Observed(at, 1, "界面", place = place)
    private fun followed(events: List<RunEvent>, places: Map<String, SkillPlaces> = emptyMap()) =
        SkillFollow.followed(events, events.filterIsInstance<RunEvent.SkillUsed>(), tools) { places[it] }

    @Test fun readThenActedCountsWhenNoAppsAreDeclared() {
        assertEquals(setOf("s"), followed(listOf(read("s", 10), act(20))))
    }

    @Test fun readingAfterTheWorkOrOnlyFailedActionsDoNotCount() {
        assertTrue(followed(listOf(act(5), read("s", 10))).isEmpty())
        assertTrue(followed(listOf(read("s", 10), act(20, ok = false))).isEmpty())
        // 读屏、查技能不是动手
        assertTrue(followed(listOf(read("s", 10), act(20, tool = "phone_observe"), act(21, tool = "skill_read"))).isEmpty())
    }

    @Test fun declaredAppMustBeVisitedAfterReading() {
        val wechat = mapOf("s" to SkillPlaces(packages = setOf("com.tencent.mm")))
        assertEquals(setOf("s"), followed(listOf(read("s", 10), act(20), saw(21, Place.app("com.tencent.mm"))), wechat))
        assertTrue(followed(listOf(read("s", 10), act(20), saw(21, Place.app("com.ss.android.ugc.aweme"))), wechat).isEmpty())
        // 进微信是在读技能之前,读完以后在别处动手
        assertTrue(followed(listOf(saw(5, Place.app("com.tencent.mm")), read("s", 10), act(20)), wechat).isEmpty())
    }

    @Test fun declaredSiteMatchesSubdomainsAndPages() {
        val bili = mapOf("s" to SkillPlaces(domains = setOf("bilibili.com")))
        assertEquals(setOf("s"), followed(listOf(read("s", 10), act(20), saw(21, Place.site("m.bilibili.com"))), bili))
        assertEquals(setOf("s"), followed(listOf(read("s", 10), act(20), saw(21, Place.page("https://www.bilibili.com/video/1"))), bili))
        assertTrue(followed(listOf(read("s", 10), act(20), saw(21, Place.site("notbilibili.com"))), bili).isEmpty())
    }

    @Test fun eachSkillIsJudgedOnItsOwn() {
        val places = mapOf("wx" to SkillPlaces(packages = setOf("com.tencent.mm")), "dy" to SkillPlaces(packages = setOf("com.ss.android.ugc.aweme")))
        val events = listOf(read("dy", 10), read("wx", 11), act(20), saw(21, Place.app("com.tencent.mm")))
        assertEquals(setOf("wx"), followed(events, places))
    }

    // 技能头部写的可能是 App 知识名(手写的),也可能是包名(自学的):都换成包名、域名
    @Test fun catalogResolvesKnowledgeNamesAndKeepsRawPackages() {
        val skills = InMemoryPersonalSkills(
            personalSkill("by-name", extraMeta = "apps: [\"wechat\"]\nsites: [\"bilibili.com\"]\n"),
            personalSkill("by-package", extraMeta = "apps: [com.example.app]\n"),
            personalSkill("plain"),
        )
        val catalog = GuideCatalog.withPersonal(GuideCatalog.loadBundled(), skills)
        val byName = catalog.skillPlaces("by-name")!!
        assertTrue("com.tencent.mm" in byName.packages)
        assertTrue("bilibili.com" in byName.domains)
        assertEquals(setOf("com.example.app"), catalog.skillPlaces("by-package")!!.packages)
        assertEquals(false, catalog.skillPlaces("plain")!!.declared)
        assertNull(catalog.skillPlaces("missing"))
    }
}
