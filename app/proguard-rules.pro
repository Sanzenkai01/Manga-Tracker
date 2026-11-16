# Room resolves the generated database implementation through a no-argument constructor.
-keep class * extends androidx.room.RoomDatabase { void <init>(); }

# OkHttp supplies its own consumer rules in META-INF/proguard/okhttp3.pro.
# Jsoup and RE2/J are used through direct calls and need no additional keep rules.
