# ML Kit (Scan receipt, Play Store flavor) starts its components by reflection, through the no-argument
# constructor of each registrar. R8 removes those constructors as unused, ML Kit then fails to start, and every
# scan fails in the minified release build.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}

# Play In-App Review (Play Store flavor) refers to this Play services annotation, which is not on the classpath and
# is not needed at run time. Without this, R8 stops the release build on the missing class.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
