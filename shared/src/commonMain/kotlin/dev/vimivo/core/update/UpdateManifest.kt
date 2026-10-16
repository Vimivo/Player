@file:Suppress("ArgumentListWrapping", "CyclomaticComplexMethod", "MaximumLineLength", "MaxLineLength", "ReturnCount")

package dev.vimivo.core.update

import kotlinx.serialization.Serializable

@Serializable
data class ReleaseArtifact(
    val platform: String,
    val format: String,
    val filename: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val architecture: String? = null,
    val minOsVersion: String? = null,
    val signature: String? = null,
    val signingKeyId: String? = null,
)

@Serializable
data class UpdateManifest(
    val product: String,
    val version: String,
    val releaseDate: String,
    val channel: String = "stable",
    val releaseNotesUrl: String? = null,
    val changelogSummary: String? = null,
    val minSupportedDatabaseVersion: Int = 1,
    val signature: String? = null,
    val signingKeyId: String? = null,
    val artifacts: List<ReleaseArtifact> = emptyList(),
) {
    fun findArtifactFor(
        platform: String,
        architecture: String? = null,
        preferredFormat: String? = null,
        strictArchitecture: Boolean = false,
    ): ReleaseArtifact? {
        val platformMatches = artifacts.filter {
            it.platform.equals(platform, ignoreCase = true)
        }
        if (platformMatches.isEmpty()) return null

        val archMatches = if (architecture != null) {
            val matching = platformMatches.filter {
                it.architecture == null ||
                    it.architecture.equals("universal", ignoreCase = true) ||
                    it.architecture.equals(architecture, ignoreCase = true)
            }
            if (matching.isNotEmpty()) {
                matching
            } else if (strictArchitecture) {
                emptyList()
            } else {
                platformMatches
            }
        } else {
            platformMatches
        }

        return if (preferredFormat != null) {
            archMatches.firstOrNull { it.format.equals(preferredFormat, ignoreCase = true) }
                ?: archMatches.firstOrNull()
        } else {
            archMatches.firstOrNull()
        }
    }
}

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prereleaseType: String? = null,
    val prereleaseNumber: Int? = null,
) : Comparable<SemanticVersion> {

    fun isNewerThan(other: SemanticVersion): Boolean = this > other

    override fun compareTo(other: SemanticVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)

        // Stable release (no prerelease) is newer than any prerelease of the same M.m.p
        if (prereleaseType == null && other.prereleaseType != null) return 1
        if (prereleaseType != null && other.prereleaseType == null) return -1
        if (prereleaseType == null && other.prereleaseType == null) return 0

        val typeOrder = mapOf("alpha" to 1, "beta" to 2, "rc" to 3)
        val thisOrder = typeOrder[prereleaseType?.lowercase()] ?: 0
        val otherOrder = typeOrder[other.prereleaseType?.lowercase()] ?: 0
        if (thisOrder != otherOrder) return thisOrder.compareTo(otherOrder)

        val thisNum = prereleaseNumber ?: 0
        val otherNum = other.prereleaseNumber ?: 0
        return thisNum.compareTo(otherNum)
    }

    companion object {
        private val SEMVER_REGEX = Regex(
            """^v?(\d+)\.(\d+)\.(\d+)(?:-([a-zA-Z]+)(?:\.(\d+))?)?$""",
        )

        fun parse(versionStr: String): SemanticVersion? {
            val match = SEMVER_REGEX.matchEntire(versionStr.trim()) ?: return null
            val major = match.groupValues[1].toInt()
            val minor = match.groupValues[2].toInt()
            val patch = match.groupValues[3].toInt()
            val preType = match.groupValues[4].takeIf { it.isNotBlank() }
            val preNum = match.groupValues[5].takeIf { it.isNotBlank() }?.toIntOrNull()
            return SemanticVersion(major, minor, patch, preType, preNum)
        }
    }
}
