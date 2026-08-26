---
name: "Authoritative Maven version lookup"
description: "maven-metadata.xml is the source of truth for a dependency's versions; pick the newest unqualified entry, since <release> may be a milestone"
type: reference
---

# Authoritative Maven version lookup

The version list of a Maven artifact lives in

```
https://repo1.maven.org/maven2/<group/path>/<artifactId>/maven-metadata.xml
```

for example
`https://repo1.maven.org/maven2/io/temporal/temporal-sdk/maven-metadata.xml`.

The `<release>` element tracks the most recently published version, which
includes pre-releases: for `org/springframework/boot/spring-boot-starter-parent`
it reads `4.2.0-M1`. The latest **stable** version is the newest `<version>`
entry in the `<versions>` list carrying no qualifier — no `-M<n>`, `-RC<n>`,
`-beta`, `-alpha`, or `-SNAPSHOT` suffix.

The `https://search.maven.org/solrsearch/select` API indexes the same
repository but lags behind it, so it reports an older version as the newest
one. context7 serves the library's API documentation and does not carry
release numbers.

**Why:** a version bump driven by the solrsearch API silently stays several
releases behind, and one driven by `<release>` alone can land on a milestone;
`maven-metadata.xml` is the file Maven itself resolves against, so its
`<versions>` list always matches what the build can download.

**How to apply:** consult context7 for the library's API and usage, and read
`maven-metadata.xml` for the version number — scan `<versions>` and take the
newest unqualified entry rather than reading `<release>`. Confirm the artifact
really exists at that version before editing a pom — request the
`<artifactId>-<version>.pom` under the version directory and expect HTTP 200.
Check every artifact of the family the build uses (for Temporal:
`temporal-spring-boot-starter` and `temporal-testing`), since they share one
`temporal.version` property.
