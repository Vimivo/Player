package dev.vimivo.core.legal

/** The app a notice applies to; each ships a different set of third-party code. */
enum class NoticePlatform { ANDROID, IOS, MACOS, WINDOWS, LINUX }

/** One third-party component shipped in an app, with the exact source it was built from. */
data class ThirdPartyComponent(
    val name: String,
    val version: String,
    val license: String,
    val sourceUrl: String,
    val platforms: Set<NoticePlatform>,
    /** Part of the desktop LGPL media package (libmpv, FFmpeg and their dependencies, ADR-004). */
    val mediaPackage: Boolean,
) {
    val isLgpl: Boolean get() = "LGPL" in license
}

/**
 * Third-party notices shown by About on every app. The component list is generated from the pinned build inputs
 * (`scripts/generate_notices.py`), so it always matches what ships.
 */
object ThirdPartyNotices {
    const val SOURCE_REPOSITORY: String = "https://github.com/Vimivo/Player"
    const val BUILD_SCRIPTS_URL: String = "$SOURCE_REPOSITORY/tree/main/scripts/build_media"
    const val NOTICES_URL: String = "$SOURCE_REPOSITORY/blob/main/docs/THIRD_PARTY_NOTICES.md"

    /** Where the media package [platform] ships is published, or null when it ships none (iOS today). */
    fun mediaReleaseUrl(platform: NoticePlatform): String? =
        MEDIA_RELEASES[platform]?.let { "$SOURCE_REPOSITORY/releases/tag/$it" }

    val components: List<ThirdPartyComponent> get() = generatedComponents

    fun forPlatform(platform: NoticePlatform): List<ThirdPartyComponent> =
        generatedComponents.filter { platform in it.platforms }

    /** The LGPL notice for [platform], or null when that app contains no LGPL-licensed code. */
    fun lgplNotice(platform: NoticePlatform): String? {
        val lgpl = forPlatform(platform).filter(ThirdPartyComponent::isLgpl)
        if (lgpl.isEmpty()) return null
        val names = lgpl.joinToString { it.name }
        val release = MEDIA_RELEASES[platform]?.let { " (media package $it)" }.orEmpty()
        val replacing = "They are separate shared libraries inside the app$release, built LGPL-only from the " +
            "published build scripts; you may replace them with your own modified build."
        return "Vimivo is MIT-licensed. This app uses libraries licensed under the GNU Lesser General Public " +
            "License version 2.1 or later: $names. $replacing The exact sources, build scripts and licence texts " +
            "are available at $SOURCE_REPOSITORY."
    }
}
