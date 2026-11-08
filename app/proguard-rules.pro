# Preserve runtime metadata used by AndroidX annotations and Kotlin reflection.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# Room generates direct accessors at compile time; retain only database model metadata.
-keep class com.duelmasters.game.data.local.** { *; }
