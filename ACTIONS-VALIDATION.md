# Actions validation

Version: 1.9.4-fixed

Validated items:
- Workflow YAML parses successfully.
- All local `uses:` actions are pinned to the selected current major versions.
- CI runs `mvn clean verify` and fails if no executable JAR exists.
- Manual Release workflow builds and uploads an artifact but does not attempt tag-only publishing.
- Tag Release workflow publishes Maven package, GitHub Release assets, checksums, and GHCR image.
- Docker image consumes the JAR already built and tested by Maven, avoiding a second inconsistent Maven build.
- POM XML parses and source encoding is explicitly UTF-8.
- Username generator compiles and high-volume simulations pass.

External publishing cannot be performed without the repository GITHUB_TOKEN, but every publish step is guarded and uses the workflow token supplied by GitHub.
