---
name: "Authoritative Maven version lookup"
description: "maven-metadata.xml <release> is the source of truth for a dependency's latest stable version; the solrsearch API lags"
type: reference
---

# Authoritative Maven version lookup

The latest stable version of a Maven artifact is the `<release>` element of

```
https://repo1.maven.org/maven2/<group/path>/<artifactId>/maven-metadata.xml
```

for example
`https://repo1.maven.org/maven2/io/temporal/temporal-sdk/maven-metadata.xml`.

The `https://search.maven.org/solrsearch/select` API indexes the same
repository but lags behind it, so it reports an older version as the newest
one. context7 serves the library's API documentation and does not carry
release numbers.

**Why:** a version bump driven by the solrsearch API silently stays several
releases behind; `maven-metadata.xml` is the file Maven itself resolves
against, so it always matches what the build can download.

**How to apply:** consult context7 for the library's API and usage, and read
`maven-metadata.xml` for the version number. Confirm the artifact really
exists at that version before editing a pom — request the
`<artifactId>-<version>.pom` under the version directory and expect HTTP 200.
Check every artifact of the family the build uses (for Temporal:
`temporal-spring-boot-starter` and `temporal-testing`), since they share one
`temporal.version` property.
