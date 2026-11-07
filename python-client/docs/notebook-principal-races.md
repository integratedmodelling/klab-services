# Independent notebook clients: race and principal boundaries

The owned-kernel regression runs the installed asynchronous SDK with an independent
HTTP client for each writer. Two synthetic credentials name the same server-owned
principal; this is **not** cross-principal collaboration or an access-grant API.
Both writers use observed revision1 for one explicit experimental private draft.
Exactly one conditional write succeeds; the other reports conflict without retry.
The owner reads revision2 and exact revision1 history. All drafts remain
`UNVALIDATED_PRIVATE_DRAFT`, not accepted or published k.IM/k.Actors source.

A third credential names a different principal. Possessing the context ID and
Runtime ID does not authorize draft reads, writes or ordinary observation graph
queries: all three are denied by the server. Closing readers does not release the
owner context, overwrite the winner or prevent further observation reads. The
owner explicitly releases its own context and session in `finally`; retained
drafts and remote objects then disappear. No extra observation submission occurs.

The notebook contains no credentials in source or outputs: bounded synthetic
credentials enter only through private runtime configuration. The existing notebook
sanitizer checks the entire executed notebook, including source, outputs and errors.
The test requires exactly three once-executed cells, exact interpreter/SDK identity,
an owned child kernel with verified shutdown, removed private connection-file
directory and no partial cleanup acknowledgement. Windows uses hidden child launch.
The existing local kernel uses authenticated loopback TCP; this test does not
certify encrypted notebook transport or hostile-host isolation.

Evidence source: **SIMULATED_PROVISIONAL**, JAVA-GAP-0009/0015. Private CAS and
synthetic principal ownership do not establish native accepted-source revision
history, semantic validation, deployed issuer/license/role approval or sharing.
Native Java execution remains **REAL_NOT_RUN**. See the executable
`developer-tools/klab-service-simulator/tests/test_notebook_principal_races.py`
and dedicated `notebook-client` workflow job. No production package or service
implementation changes are required for this acceptance scenario.
