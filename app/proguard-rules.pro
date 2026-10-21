# Orbit has no reflection of its own: every component is reached through an explicit
# ComponentName, and the manifest entries are kept by the rules aapt generates. Compose, the
# AndroidX libraries and the profile installer ship their own rules. So there is nothing to keep
# here beyond readable stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
