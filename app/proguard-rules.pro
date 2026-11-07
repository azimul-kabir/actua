# ML Kit (Scan receipt, Play Store flavor) starts its components by reflection, through the no-argument
# constructor of each registrar. R8 removes those constructors as unused, ML Kit then fails to start, and every
# scan fails in the minified release build.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
