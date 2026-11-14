package com.onpath.app.poi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Wikipedia picture lookup (online extra): which article, what is sent, how the answer is read. */
class WikiPicturesTest {
    private fun answer(vararg pages: String) = """{"batchcomplete":true,"query":{"pages":[${pages.joinToString(",")}]}}"""
    private fun page(title: String, thumb: String? = "https://upload.wikimedia.org/x/$title.jpg") =
        """{"pageid":1,"title":"$title","fullurl":"https://de.wikipedia.org/wiki/$title"""" +
            (if (thumb != null) ""","thumbnail":{"source":"$thumb","width":640,"height":427}""" else "") + "}"

    @Test fun tagWithAndWithoutLanguage() {
        assertEquals("de" to "Zugspitze", WikiPictures.fromTag("de:Zugspitze", "en"))
        assertEquals("en" to "Kaisergebirge", WikiPictures.fromTag("Kaisergebirge", "en"))
        assertNull(WikiPictures.fromTag(" ", "de"))
    }

    @Test fun theGeosearchSendsOnlyARoundedPosition() {
        val u = WikiPictures.geoUrl("de", 47.540219, 12.210187, 1500)
        assertTrue(u, u.contains("ggscoord=47.540%7C12.210"))
        assertFalse(u.contains("47.5402"))
        assertTrue(u.startsWith("https://de.wikipedia.org/w/api.php?"))
    }

    @Test fun theArticleMustBeNamedLikeThePoi() {
        assertTrue(WikiPictures.titleMatches("Pendling (Berg)", "Pendling"))
        assertTrue(WikiPictures.titleMatches("Hohe Salve", "hohe  salve"))
        assertFalse(WikiPictures.titleMatches("Kufstein", "Pendling"))
        val pic = WikiPictures.parse(answer(page("Kufstein"), page("Pendling (Berg)")), "Pendling")!!
        assertEquals("Pendling (Berg)", pic.title)
        assertEquals("https://upload.wikimedia.org/x/Pendling (Berg).jpg", pic.thumbUrl)
        assertNull("no article with that name nearby: no picture", WikiPictures.parse(answer(page("Kufstein")), "Pendling"))
    }

    @Test fun anArticleWithoutPictureOrABrokenAnswerGivesNone() {
        assertNull(WikiPictures.parse(answer(page("Zugspitze", thumb = null)), null))
        assertEquals("Zugspitze", WikiPictures.parse(answer(page("Zugspitze")), null)!!.title)
        assertNull(WikiPictures.parse("not json", null))
        assertNull(WikiPictures.parse("""{"query":{}}""", "X"))
    }

    @Test fun peaksAreSearchedWiderThanHuts() {
        assertEquals(1500, WikiPictures.radiusFor("peak"))
        assertEquals(500, WikiPictures.radiusFor("hut"))
    }
}
