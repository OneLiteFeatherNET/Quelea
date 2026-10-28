// gradle.properties' `version` line carries a `# x-release-please-version` marker comment that
// release-please rewrites on every release. Java .properties syntax only treats `#` as a comment
// starter at the start of a line, so the raw value includes that trailing comment - strip it here.
allprojects {
    group = "net.onelitefeather"
    version = (version as String).substringBefore('#').trim()
}
