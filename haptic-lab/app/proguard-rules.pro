-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-keepclassmembers class com.tepmex.hapticlab.** {
    *** Companion;
}
-keepclasseswithmembers class com.tepmex.hapticlab.** {
    kotlinx.serialization.KSerializer serializer(...);
}
