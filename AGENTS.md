# Guidelines for AI Agents

## Core Rules

- Update documentation when the feature is added, removed or changed.
- Verify changes by running tests after making changes.
- Use version catalog (`gradle/libs.versions.toml`) for dependency management.

## Project Structure

- `simplecarousel/`: Core library containing carousel components for `RecyclerView`.
- `simplecarousel-pager/`: Extended library containing pager components similar to `ViewPager2` built on top of `simplecarousel`.
- `sample/`: Sample application.

## Commands

- `./gradlew build`: Build project.
- `./gradlew test`: Run all tests.

## Commit Style

- Use concise, atomic commit message.
- Start with a capitalized imperative verb.
