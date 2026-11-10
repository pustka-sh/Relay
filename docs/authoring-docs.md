# Writing documentation

Relay's documentation is authored here, alongside the code it describes. The documentation workflow combines Markdown guides with API references extracted from real Java declarations and Javadoc comments. The website reads the resulting versioned package; it never executes project code.

## Document the public API in Java

Use ordinary Javadoc comments supported by Java 21. Keep the first sentence a useful summary, then explain behavior, constraints and examples. Describe the contract rather than repeating the method name.

```java
/**
 * Looks up the backend registered under a name.
 *
 * <p>The name is matched exactly. See {@link #backends()} for the current set.</p>
 *
 * @param name the configured backend name
 * @return the registered backend, or an empty result when it is unknown
 * @throws IllegalArgumentException if the name is empty
 * @since 0.2.0
 */
```

This is an authoring example, not an assertion that this API currently exists. The extractor preserves author-written descriptions, `@param`, `@return`, `@throws`, `@since`, `@deprecated`, `{@code ...}`, `{@literal ...}` and resolvable `{@link ...}` references. For deprecation, use Java's `@Deprecated` annotation and describe the replacement in `@deprecated`.

The current pilot exports the proxy module's public and protected declarations, including visible nested classes, records, interfaces, enums and annotations. Private and package-private declarations are excluded. Add `@hidden` to deliberately exclude an otherwise visible declaration. An undocumented declaration is shown as undocumented; the generator does not invent an explanation. `{@inheritDoc}` and custom taglets are not expanded in this first adapter, so write an explicit description when needed.

API links use the package's exact source commit and original line number. Overloaded methods have separate stable IDs based on their erased parameter types. Renaming a parameter or improving its description does not change its link.

## Write guides in Markdown

Add a UTF-8 `.md` guide and register its path, URL slug and title in the root `pustka.docs.json`. Paths stay relative to this repository. The manifest's `api.artifact` names the generated API JSON; it is not a command to run.

Use fenced code blocks for configuration, commands and multi-line Java examples. The reader supports standard Markdown and a safe subset of LaTeX math through KaTeX:

```markdown
An inline expression: $t = d / v$.

$$
\sum_{i=1}^{n} p_i = 1
$$
```

For example, a distribution of route weights obeys $\sum_{i=1}^{n} p_i = 1$:

$$
p_i = \frac{w_i}{\sum_{j=1}^{n} w_j}
$$

This is mathematical notation inside Markdown, rather than complete `.tex` documents. Raw HTML is escaped, rendered output is sanitized, and math cannot load external resources. Keep operational secrets and generated machine configuration out of guides.

## Generate a package locally

Install Java 21 and Node 24. Check out the Site repository at the exact toolchain commit pinned in `.github/workflows/docs.yml`, alongside Relay. From Relay:

```sh
bash ./gradlew generatePustkaDocs -PpustkaDocsToolchain=/absolute/path/to/Site
```

On Windows use `gradlew.bat`. The task uses the proxy's real Gradle source set and compilation classpath. It compiles the documentation adapter for Java 21 and writes `build/pustka-docs/api.json`; it does not change either module's Java runtime target.

From the pinned Site checkout, install its lockfile dependencies with `npm ci --ignore-scripts`, then package the guides and API. Replace the two checkout paths and the commit SHA with actual values from `git rev-parse HEAD` in Relay:

```sh
node scripts/package-docs.mjs \
  --root /absolute/path/to/Relay \
  --manifest /absolute/path/to/Relay/pustka.docs.json \
  --api /absolute/path/to/Relay/build/pustka-docs/api.json \
  --out /absolute/path/to/Relay/build/pustka-docs/package.json \
  --version main --ref refs/heads/main --commit YOUR_40_CHARACTER_COMMIT_SHA
```

For a release, use the exact tag as `--version` (for example `v0.2.0`) and `refs/tags/v0.2.0` as `--ref`. A compilation, unresolved Java type, unsafe path or invalid package fails the job before publication.

## Automatic publication

Pull requests generate and validate the package and attach it as the `relay-documentation` workflow artifact. They do not receive publication secrets or publish to the website. Their package uses `main` metadata with the actual checked-out PR commit and is for review only.

After a push to this repository's `main` branch, the workflow publishes the development snapshot. You can also run the Documentation workflow manually on `main` to publish the initial snapshot. Manual runs on other branches only generate review artifacts. Semantic version tags such as `v0.2.0` or `v0.2.0-rc.1` publish an immutable release; other tag formats fail package validation. Publication runs in a separate job that does not check out or execute Relay's code. The `main` snapshot can change, while previously published release versions stay available.

Before enabling publication, the site operator registers Relay's repository and a random project key in the Docker server's `DOCS_PUBLISH_KEYS` configuration, then sets the same key as this repository's Actions secret `PUSTKA_DOCS_PUBLISH_KEY`. Use at least 32 random characters. The key belongs only in those two settings, never this repository, an artifact or browser code. The signed publisher sends to `https://pustka.sh/api/site/docs/publish` over HTTPS. A failed upload leaves the website's last good documentation intact.

The workflow pins the Site toolchain and all actions to full commit hashes. Update and review that pin deliberately when adopting a new adapter or package format. Java extraction lives in Site's `tools/javadoc`; future project adapters can emit the same documentation model without changing this reader.
