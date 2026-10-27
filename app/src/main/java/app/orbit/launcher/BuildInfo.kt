package app.orbit.launcher

/** What a build says about itself. Pinned by [ShotHooks.build] in a screenshot test. */
data class BuildStamp(
    val version: String,
    val channel: String,
    val commit: String,
    val modified: Boolean,
    /** The release this commit is, or empty when it is not a tagged one. */
    val tag: String,
    val builtOn: String,
)

/**
 * Which build this is, for checking the television against the repository.
 *
 * The version alone cannot tell two builds of one version apart, which is exactly the question
 * when a change has been merged but the set has not been updated. The commit answers it, and the
 * tag says whether what is running was ever released at all.
 */
object BuildInfo {

    private val real = BuildStamp(
        version = BuildConfig.VERSION_NAME,
        // What the owner installed: a release build, or one made while working on it.
        channel = if (BuildConfig.DEBUG) "debug" else "stable",
        // Short, because a television is read from across a room, and it is enough to match on.
        commit = BuildConfig.GIT_SHA,
        modified = BuildConfig.GIT_MODIFIED,
        tag = BuildConfig.GIT_TAG,
        builtOn = BuildConfig.BUILT_ON,
    )

    private val stamp: BuildStamp get() = ShotHooks.build ?: real

    val version: String get() = stamp.version
    val channel: String get() = stamp.channel
    val commit: String get() = stamp.commit

    /** "0.2.0 stable", the line under the name. */
    fun line(): String = "${stamp.version} ${stamp.channel}"

    /**
     * The commit, said out loud when the checkout it was built from had changes on top: the
     * commit named is then not quite what is running.
     */
    fun source(): String =
        if (stamp.modified) "${stamp.commit}, with changes on top" else stamp.commit

    /**
     * Which release this is, or that it is none.
     *
     * A build from an untagged commit is something that was never released, which is worth
     * knowing when the television is being compared against the repository.
     */
    fun release(): String =
        if (stamp.tag.isNotBlank()) "released as ${stamp.tag}" else "between releases"

    /** "built 2026-10-27". The day is enough to place a build; the commit identifies it. */
    fun built(): String = "built ${stamp.builtOn}"
}
