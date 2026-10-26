package app.orbit.launcher

/**
 * Which build this is, for checking the television against the repository.
 *
 * The version alone cannot tell two builds of one version apart, which is exactly the question
 * when a change has been merged but the set has not been updated. The commit answers it.
 */
object BuildInfo {

    val version: String = BuildConfig.VERSION_NAME

    /** What the owner installed: a release build, or one made while working on it. */
    val channel: String = if (BuildConfig.DEBUG) "debug" else "stable"

    /** Short, because a television is read from across a room, and it is enough to match on. */
    val commit: String = BuildConfig.GIT_SHA

    /**
     * Whether the checkout had uncommitted changes when this was built. Said out loud, because
     * then [commit] names a commit that is not what is running.
     */
    val modified: Boolean = BuildConfig.GIT_MODIFIED

    /** "0.2.0 stable", the line under the name. */
    fun line(): String = "$version $channel"

    /** "a1b2c3d4e5f6", or that with a word about the tree it was built from. */
    fun source(): String = if (modified) "$commit, with changes on top" else commit
}
