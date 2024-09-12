package org.lifeutils.nhentaidl.scraper

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.lifeutils.nhentaidl.model.HentaiId
import org.lifeutils.nhentaidl.model.HentaiInfo
import org.lifeutils.nhentaidl.model.HentaiMetadata
import org.lifeutils.nhentaidl.model.Language
import java.time.ZonedDateTime

private const val METADATA_SELECTOR = "div#info"
private const val TITLE_SELECTOR = "$METADATA_SELECTOR h1.title span"
private const val TAGS_SELECTOR = "$METADATA_SELECTOR section#tags div.tag-container"
private const val TAG_VALUE_SELECTOR = "span.name"
private const val PAGES_SELECTOR = "div.thumbs div.thumb-container"

data class HentaiScrapResult(
    val hentaiInfo: HentaiInfo,
    val pages: List<HentaiPage>
)

data class HentaiPage(
    val page: Int,
    val fileName: String,
    val url: String
)

fun scrapHentaiTitlePage(id: HentaiId, content: String): HentaiScrapResult {
    val doc = Jsoup.parse(content)

    val hentaiInfo = extractHentaiInfo(id, doc)
    val extractPages = extractPages(doc)

    return HentaiScrapResult(
        hentaiInfo = hentaiInfo,
        pages = extractPages
    )
}

private fun extractHentaiInfo(id: HentaiId, doc: Document): HentaiInfo {
    val title = doc.select(TITLE_SELECTOR).joinToString(separator = " ", transform = Element::text).trim()

    val tagsElement = doc.select(TAGS_SELECTOR)
    val tags = tagsElement.mapNotNull(::extractTags).toMap()
    val pageCount = tagsElement.firstNotNullOf(::extractPagesTag)
    val uploaded = tagsElement.firstNotNullOf(::extractUploadedTag)

    return HentaiInfo(
        id = id,
        title = title,
        pageCount = pageCount,
        metadata = HentaiMetadata(
            parodies = tags.tag(TagType.Parodies),
            characters = tags.tag(TagType.Characters),
            tags = tags.tag(TagType.Tags),
            artists = tags.tag(TagType.Artists),
            language = tags.tag(TagType.Languages).mapNotNull { Language.fromString(it) },
            groups = tags.tag(TagType.Groups),
            categories = tags.tag(TagType.Categories),
            uploadedAt = uploaded,
        ),
    )
}

private fun extractTags(tagElement: Element): Pair<TagType, List<String>>? {
    val tagText = tagElement.text().substringBefore(":")

    val type = TagType.fromString(tagText)
    if (type == null || type.isSpecialExtractor) {
        return null
    }

    val tagValues = tagElement.select(TAG_VALUE_SELECTOR).map(Element::text)
    return type to tagValues
}

private fun extractPagesTag(tagElement: Element): Int? {
    val tagText = tagElement.text().substringBefore(":")

    val type = TagType.fromString(tagText)
    if (type != TagType.Pages) {
        return null
    }

    return tagElement.selectFirst(TAG_VALUE_SELECTOR)!!.text().toInt()
}

private fun extractUploadedTag(tagElement: Element): ZonedDateTime? {
    val tagText = tagElement.text().substringBefore(":")

    val type = TagType.fromString(tagText)
    if (type != TagType.Uploaded) {
        return null
    }

    val timeText = tagElement.select("time").attr("datetime")
    return ZonedDateTime.parse(timeText)
}

private fun Map<TagType, List<String>>.tag(tagType: TagType): List<String> {
    return get(tagType) ?: emptyList()
}

private fun extractPages(doc: Document): List<HentaiPage> {
    return doc.select(PAGES_SELECTOR)
        .map {
            val pageUrl = it.selectFirst("noscript > img")!!
                .attr("src")

            val thumbBaseUrl = pageUrl.substringBeforeLast("/")
            val juicyBaseUrl = thumbBaseUrl.replace(Regex("t(?=\\d+\\.)"), "i")

            val thumbFile = pageUrl.substringAfterLast("/")
            val fileExtension = thumbFile.substringAfterLast(".")
            val fileIndex = thumbFile.substringBeforeLast("t.")
            val juicyFileName = "$fileIndex.$fileExtension"

            HentaiPage(
                page = fileIndex.toInt(),
                fileName = juicyFileName,
                url = "$juicyBaseUrl/$juicyFileName"
            )
        }
}

private enum class TagType(val isSpecialExtractor: Boolean = false) {
    Parodies,
    Characters,
    Tags,
    Artists,
    Groups,
    Languages,
    Categories,
    Pages(isSpecialExtractor = true),
    Uploaded(isSpecialExtractor = true);

    companion object {
        fun fromString(tagText: String): TagType? {
            return entries.find { it.name.equals(tagText, ignoreCase = true) }
        }
    }
}
