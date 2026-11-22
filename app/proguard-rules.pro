# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class filehub.android.**$$serializer { *; }
-keepclassmembers class filehub.android.** {
    *** Companion;
}
-keepclasseswithmembers class filehub.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
