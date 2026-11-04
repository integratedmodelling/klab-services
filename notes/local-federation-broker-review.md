# Explicit local-federation broker configuration

Contributor-guide author-agent review; no maintainer approval inferred.
Base: accepted delivery `c0644045f4fa1a0ff37601a30e5952655672ce96`.

`Federation.local()` previously selected only the fixed default broker. An owned
managed-agent acceptance stack must not connect to Modeler's broker. Introduce
the deployment-owner JVM property `klab.local.broker.uri`, leaving the absent-property
default and federation identity unchanged. This is not a remote advertisement,
authorization/group change or automatic broker provisioner.

Explicit values require AMQP(S), host, explicit valid port and nonempty virtual
host; reject queries/fragments and malformed syntax. Errors contain neither the
URI nor parser cause (which may include credentials). Operators must secure JVM
arguments and logs as private credential material; this option does not make
plain AMQP encrypted or restrict remote operator-selected brokers to loopback.

Local offline Runtime-server dependency reactor: **224 tests**, no failures,
errors or skips (original222 plus2 configuration methods). Regression tests
cover unchanged default, private loopback URI preservation, explicit TLS URI and
malformed/ambiguous credential-safe rejection. They do not connect to any broker.
Exact diff, untracked-test hash and logs are in the established evidence root,
`isolated-broker-configuration-reactor-20261104`.

Review: no demonstrated defect in this narrow configuration correction. Dedicated
hosted gate added; merge only after current hosted checks pass. This does not
prove managed lifecycle or solve any separately diagnosed parent messaging
initialization issue. PR138 remains draft until its applicable acceptance passes.
