# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ================================================================================
# ### START: CRITICAL FIX FOR EMAIL FUNCTIONALITY ###
# The JavaMail and Activation libraries use reflection, which breaks when code is
# obfuscated by Proguard/R8. These rules preserve the necessary classes.
# ================================================================================

# Keep all classes in the JavaMail API packages. The '*' keeps all methods and fields.
-keep class javax.mail.** { *; }
-keep class javax.mail.internet.** { *; }

# Keep all classes in the Java Activation Framework package.
-keep class javax.activation.** { *; }

# The mail library has a dependency on java.beans, which also needs to be preserved.
-keep class java.beans.** { *; }

# Suppress warnings about optional dependencies that these libraries might reference
# but are not included in the Android runtime. This prevents build warnings/failures.
-dontwarn java.beans.**
-dontwarn javax.activation.**
# ================================================================================
# ### END: CRITICAL FIX FOR EMAIL FUNCTIONALITY ###
# ================================================================================