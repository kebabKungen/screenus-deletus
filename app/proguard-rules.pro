# ---- Obfuscation hardening ----
-keepattributes *Annotation*,SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile
-repackageclasses
-allowaccessmodification
# Ignored by R8 (it always optimizes fully) but kept for ProGuard compatibility.
-optimizationpasses 5

# ---- SQLCipher: native code calls back into these classes by name ----
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }

# ---- Tink: optional dependencies referenced by key-download helpers we don't use ----
-dontwarn com.google.api.client.http.**
-dontwarn com.google.api.client.extensions.android.http.**
-dontwarn org.joda.time.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# ---- Components referenced from the manifest are kept automatically by AAPT. ----
# Strip debug logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
