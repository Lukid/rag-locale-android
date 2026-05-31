# Regole ProGuard/R8. Per l'M1 l'offuscamento è disattivato (vedi build.gradle.kts);
# queste regole restano come base per quando verrà riattivato.

# LiteRT-LM e MediaPipe usano JNI/reflection: non rinominare/rimuovere.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.google.mediapipe.** { *; }
