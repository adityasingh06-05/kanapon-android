# kotlinx.serialization keeps generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class app.kanapon.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
