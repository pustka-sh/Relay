# API stability and integration scope

Relay's reference includes public and protected Java declarations so source can be inspected. Visibility is not an offer to integrate with every class. The manifest declares the labels packaged with this snapshot; no rule is inferred from `public`.

## Supported wire surface

The `dev.relay.api.ClientApi` and `dev.relay.api.backend.BackendApi` type families are labelled **supported** only as descriptions of the documented client/backend channel names, message identifiers, field encoding and limits. Read the [Client API](client-api.md) and [Backend API](backend-api.md) guides for the actual behavior, trust model and scope.

This label does not promise a stable Java ABI, a Maven artifact, semantic versioning of implementation classes, or an in-process proxy plugin API. The proxy currently has no shipped in-process plugin extension API. A mod can implement the documented bytes without embedding Relay; a backend can use the documented BungeeCord-compatible plugin-message format without linking proxy classes.

The Client API's channel/version negotiation is described in [its versioning section](client-api.md#versioning). The backend's BungeeCord-compatible subchannels and Relay-only additions are identified in the backend guide. Evaluate changes to those contracts explicitly when reviewing a new source snapshot.

## Internal implementations

All other exported proxy declarations default to **internal**, including protocol codecs, forwarding helpers, sessions, routing and API handlers. They are visible for explanation and diagnostics, and can change with implementation work. A rule naming `BackendApi` does not extend to `BackendApiHandler`.

The [cookbook](integration-cookbook.md) compiles selected helpers against current source to keep examples accurate. That does not convert those helpers into a compatibility promise. Paper plugin classes are not yet part of this proxy-only extracted reference; their real sources and classpath are checked by the cookbook build. Deploy matching Paper packet implementations on both ends, as explained in [typed packets](server-packets.md).

No surface is marked experimental merely because it is undocumented. If an intentionally offered experiment is added, its author should add a narrow `experimental` rule and explain the constraints. Older packages with no policy display **Unclassified** instead of assuming support.

## Version and migration review

`main` is a changing development snapshot. A version selector only represents packages actually published; it is not evidence that a release exists. Source links identify the exact packaged commit.

The comparison view can highlight changed declarations, documentation and policy between two available versions. It cannot establish wire, behavior or binary compatibility. Follow the [migration checklist](migration.md), test against the destination server and publish reviewed instructions alongside any actual release that changes integrations.
