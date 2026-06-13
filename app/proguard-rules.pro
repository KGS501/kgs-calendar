# ical4j parses incoming CalDAV payloads via reflection / ServiceLoader / property files, so the
# whole library (classes + members) must survive R8.
-keep class net.fortuna.ical4j.** { *; }
-keep class at.bitfire.dav4jvm.** { *; }

# Reflection-driven parsing relies on generic signatures and annotations being retained.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations

# ical4j optionally references libraries we don't bundle (Groovy DSL, SLF4J, commons, JCache).
# They're never reached at runtime for our parse/serialize paths, so silence the missing refs
# instead of letting R8 fail the build on them.
-dontwarn org.codehaus.groovy.**
-dontwarn groovy.**
-dontwarn org.apache.commons.**
-dontwarn org.slf4j.**
-dontwarn javax.cache.**
-dontwarn org.threeten.**
-dontwarn aQute.**
# Unused ical4j optional features: the jparsec-based FilterExpressionParser, the JSON-schema
# validator, and the Groovy DSL (which references java.beans, absent on Android).
-dontwarn org.jparsec.**
-dontwarn java.beans.**
-dontwarn com.github.erosb.jsonsKema.**
# Compile-only annotations referenced by Tink (androidx.security.crypto) and others.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
# org.xmlpull.v1 is provided by the Android framework (dav4jvm uses the platform parser).
-dontwarn org.xmlpull.**
