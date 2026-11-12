// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // AGP 내장 Kotlin이 사용할 KGP 버전을 2.4.x로 고정 (Readium 3.4.0 메타데이터 호환)
    alias(libs.plugins.kotlin.android) apply false
}