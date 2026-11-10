# Migrating an integration

This is a review foundation for actual version changes, not a release history. It does not assert that any tagged release or breaking change has shipped. `main` is a development snapshot; use its exact source commit when recording a test result.

## Choose real snapshots

Use the website's published version selector and comparison view to choose your current and target versions. With fewer than two published snapshots there is no meaningful version comparison yet. Do not invent a baseline or assume that a missing release is compatible. Record both source commits and retain the old package/build while validating an upgrade.

## Review the surface you use

1. Identify whether you use the [client wire protocol](client-api.md), [backend wire protocol](backend-api.md), [typed backend packets](server-packets.md) or internal Java classes. Read the [stability scope](api-stability.md); a supported wire label is not a Java binary compatibility guarantee.
2. Compare message identifiers, channel names, field order and encoding, limits, permissions and failure behavior. Structural API diffs may miss a changed constant value or implementation behavior; inspect source and authored guides too.
3. For client integrations, verify Hello/Welcome negotiation and only expose advertised capabilities. A breaking protocol would require the version/channel policy described in the client guide; do not simply send old bytes on a new channel.
4. For backend integrations, keep Bukkit's `BungeeCord` registration name, check `backend-api`, and test replies with a real online carrier. A timeout means an unknown outcome, not permission to repeat a state-changing request.
5. For typed packets, deploy identical packet classes, registration order and field layout on both ends. Registry fingerprints do not detect field-layout changes within an unchanged class name. Add application-level acknowledgement/idempotency where delivery matters.
6. Recompile the [checked recipes](integration-cookbook.md) and your own integration against the target build. Run real server and mod-loader smoke tests, including permissions changing mid-session, an empty backend, refused switches, malformed messages and timeouts.
7. Roll out on a small test network, keep matching proxy/Paper/plugin artifacts available for rollback, and verify that failures leave players connected safely.

## Write migration notes for a real change

When a release actually changes integration behavior, add a dated section here with its real version and source commits. State who is affected, the old and new behavior, concrete configuration/code changes, the tested deployment sequence and rollback constraints. Link the relevant comparison and source. If an internal helper changed, say so without implying a wider wire break.

Document an unchanged contract only after reviewing it; a clean signature comparison is not proof of compatibility. Do not include operational secrets or users' real network addresses in examples.
