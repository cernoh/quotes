# R8 rules for the release build.
#
# The app reaches the framework only, so R8 has little to remove. These rules
# keep the entry points that Android finds by name, and keep line numbers so a
# crash report stays readable.

# Components Android instantiates from the manifest.
-keep class dev.cernoh.quotes.QuotesWidgetProvider { *; }
-keep class dev.cernoh.quotes.UpdateResultReceiver { *; }
-keep class dev.cernoh.quotes.MainActivity { *; }
-keep class dev.cernoh.quotes.SettingsActivity { *; }

# Shizuku hands the binder to this provider, which the manifest names.
-keep class rikka.shizuku.ShizukuProvider { *; }

# Keep the file name and the line numbers in stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
