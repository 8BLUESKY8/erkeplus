# R8 rules for erke++.
# ASCII-only file: keep it that way so Windows tooling never guesses the encoding.

# Keep line numbers so crash stacks stay readable in release builds.
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,InnerClasses

# kotlinx-serialization resolves descriptors through annotations at runtime.
# This project only uses the JSON tree API today, so this is a safety net.
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-dontwarn kotlinx.serialization.**
