// Intentionally minimal. Each module declares and applies its own plugins via
// the version catalog (see engine/build.gradle.kts and app/build.gradle.kts).
//
// The root project is always configured (even under configuration-on-demand),
// so it must NOT reference the Android Gradle Plugin here — doing so would force
// AGP resolution from Google Maven and break `:engine:test` in environments
// where Google Maven is unreachable.
