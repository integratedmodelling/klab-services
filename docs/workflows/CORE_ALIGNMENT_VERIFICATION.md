# Core alignment correction and verification

Independent implementation, 2026-11-06, based on klab-services
`bd39c7fc72c6af0041b5f0cba0cadba54d73220c`. The incomplete transferred patch was
not applied or reconstructed. All changes and build outputs are in the isolated
`klab-services-verification` clone.

## Changes

Root alias validation first selects declarable specializations. Only when none
are present does it consider generic QUALITY and EXTENT. Generic aliases must
resolve to canonical `odo:Quality`, `odo:Extent`, `odo:Time`, or `odo:Space` as
appropriate. Specific kinds retain precedence; multiple specific or generic
kinds remain ambiguous. Existing canonical Domain checks and disabled blanket
missing/duplicate-slot warnings are preserved. No global declarability changes
or ontology axioms are introduced.

Independent Domain-to-Configuration target checks retain their existing witness
and complete-conjunction validation. Their satisfiability evidence is stored in
`urn:klab:validation:description-independent-target-status`, separately from
missing universal bearer contributions. An independent target with unresolved
satisfiability therefore does not generate DESCRIPTION_BEARER_UNRESOLVED. A
Domain with only independent targets receives no spurious owner-bearer
compatibility annotation. Quality descriptions still report missing bearer
contributions, and proven target contradictions still reject before writes.

## Coverage

- CoreDomainAliasValidationTest: canonical generic aliases in both orders,
  specific Mass precedence, delegate identity, invalid endpoints, ambiguous
  generic/specific kinds, and existing Domain cases.
- WorldviewDeclarationSupportTest: bearer-free Configuration retains independent
  unresolved evidence without a Domain bearer warning; Quality still warns;
  contradictory independent targets reject before writes.
- BroadDescriptionContractTest: actual pinned ODO aliases and existing DL,
  independent-witness, and cardinality contradiction cases. Independent witness
  evidence now asserts the independent annotation rather than a bearer warning.

## Private core provenance

The private catalog is `../pinned-core`, outside the isolated Git tree.
`odo.owl` is copied byte-for-byte from the clean local odo-im checkout at
`df5980e36d921233cc66b0bb270a2a36f86ccfce`, `releases/0.2.0/odo.owl`.
SHA-256: `5c020dac9bd9d1cd4f9673b8075cf08f75ecc7194c74378bdb8f0498110ddba3`.
The companion `klab.owl` and `dc.owl` are copied from this klab-services base.

## Validation status

Java 21 (Temurin 21.0.12) / Maven 3.9.6 offline reactor verification passed.
All five reactor projects succeeded; all Reasoner production and test sources
compiled. Results: **46 tests, zero failures, zero errors, zero skipped**:

- CoreDomainAliasValidationTest: 11 passed.
- WorldviewDeclarationSupportTest: 10 passed.
- BroadDescriptionContractTest: 25 passed, using the private pinned catalog above.

The successful command, from this clone in PowerShell, was:

```powershell
$env:JAVA_HOME='C:\Users\Ferd\.jdks\temurin-21.0.12'
mvn -o -pl klab.services.reasoner -am `
  '-Dtest=CoreDomainAliasValidationTest,WorldviewDeclarationSupportTest,BroadDescriptionContractTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  '-Dklab.test.core.directory=C:\Users\Ferd\Documents\Codex\2026-11-06\task\pinned-core' `
  '-DargLine=-javaagent:C:\Users\Ferd\.m2\repository\org\mockito\mockito-core\5.18.0\mockito-core-5.18.0.jar' test
```

The first focused run passed the 11 alias cases but stalled during Mockito's
dynamic self-attachment with a Windows pipe permission error. Only that run's
confirmed Maven/test JVMs and its stalled diagnostic helper were ended. The
successful run loads the existing Mockito agent at JVM startup; no security
settings were changed. An earlier invocation failed because PowerShell split an
unquoted dotted Maven property; the successful invocation quotes all properties.

`git diff --check` passes. The successful complete log is
`../core-alignment-pinned-test.log`; JUnit XML/text reports are in
`klab.services.reasoner/target/surefire-reports`. No tests against a running
service were performed. Live warning clearance awaits applying the reviewed fix
through the normal deployment process and checking the exact live worldview.
## Publication preparation

The user authorized committing and pushing this Services fix to develop after
checks on the final commit. Remote develop was verified at the exact base above.
Independent review found no actionable correctness issues. The reviewed code is
unchanged; final-commit test and remote/CI results are reported in the handoff.

The Services bundled ODO and odo-im `releases/0.2.0/odo.owl` are byte-identical at
the SHA-256 recorded above. No ontology semantic update is needed for this fix.
The source project's `releases/latest/odo.owl` is stale: its SHA-256 is
`496bc6634392d7e2eb2a6749bca6a1570333ea607b9b8e52ffaf5b619bcf2a57`, and it lacks
the existing `odo:impliesPredicate` object property added in odo-im commit
`df5980e36d921233cc66b0bb270a2a36f86ccfce`. The user subsequently proposed removing
the mutable latest alias. Reference and compatibility assessment is separate;
no ODO deletion or synchronization is authorized for publication here, and no
historical ODO release is changed.

No existing user checkout or service has been modified. Deployment, service
restart, and security setting changes remain outside this work.
