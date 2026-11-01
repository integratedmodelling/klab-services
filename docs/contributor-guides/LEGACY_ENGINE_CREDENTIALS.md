# Legacy engine credential contract

The legacy hub-signed engine entry point requires:

- A signature verified with the trusted hub's public key and that exact issuer.
- Audience `engine`, either alone or included in an audience list.
- A nonblank subject, an expiration time, and an issued-at time.
- An unexpired credential, with the existing thirty-second clock-skew allowance.

Missing or wrong audiences are rejected, not mapped to another recipient. Obtain
an engine-targeted token from the trusted issuer; do not remove audience validation
to accommodate a token issued for another application.

Claim validation finishes before an authenticated identity or its roles are
assigned. Without independent private local-service-key authority, rejected JWTs
cannot create an identified user scope or reuse a
previous principal. Supplying an invalid legacy credential returns HTTP403 before
controller dispatch, including on public endpoints. Explicit anonymous credentials
remain anonymous and can continue to public routing; endpoint authorization still
applies. An unexpected non-fatal validation failure clears the principal and propagates a
generic server failure, not a permission denial or arbitrary sensitive cause.
Rejection logs contain fixed messages/exception class names, not tokens,
claims, signatures or arbitrary verifier exception messages.

This policy is distinct from browser credentials handled by `HubWebAuthentication`
and the explicit private local-service-key authority path. It does not expand
either path, make anonymous requests authenticated, or grant caller headers any
authority to replace the signed identity.
