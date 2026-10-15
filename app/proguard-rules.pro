# Keeps what the shrinker cannot see being used. Room, Hilt, Compose and DataStore ship their
# own rules, so this file only covers the app's own entry points.

# The reminder receiver finds its scheduler through a Hilt entry point.
-keep @dagger.hilt.EntryPoint interface * { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Readable stack traces in Play Console crash reports once the mapping file is uploaded.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
