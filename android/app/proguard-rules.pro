# The gomobile-generated bindings are called reflectively by the Go runtime
# glue, so they must survive shrinking.
-keep class go.** { *; }
-keep class iprocker.** { *; }
-keep class mobile.** { *; }

# Kotlin serialization keeps generated serializers referenced only by name.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.qezawat.iprocker.**$$serializer { *; }
-keepclassmembers class com.qezawat.iprocker.** {
    *** Companion;
}
-keepclasseswithmembers class com.qezawat.iprocker.** {
    kotlinx.serialization.KSerializer serializer(...);
}
