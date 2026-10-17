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

# ── ManagerFoot ────────────────────────────────────────────────────────────
# Os enums do domínio são persistidos como String (nome da constante) tanto no
# Room (TypeConverters .name/valueOf) quanto no seed_data.json. Se o R8 renomear
# as constantes, `Enum.valueOf("GOLEIRO")` quebra em runtime (crash no release).
# Preserva TODOS os membros dos enums do app (constantes + values()/valueOf).
-keepclassmembers enum br.com.managerfoot.** { *; }

# Entities do Room são desserializadas por código gerado; mantém os campos p/
# garantir o mapeamento coluna→campo mesmo após ofuscação.
-keep class br.com.managerfoot.data.database.entities.** { *; }
-keepclassmembers class br.com.managerfoot.data.database.entities.** { <fields>; }

# Stacktraces legíveis em crashes de release.
-keepattributes SourceFile,LineNumberTable