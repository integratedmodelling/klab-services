# Collective lineage consumer alignment

This isolated candidate replaces only the bundled ODO with numbered 0.2.1 from
local ODO commit ede866637f505dfb882b7b9427a8f21f27922093. The exact SHA-256 is
d6ca3d02d560d6af7f5b8d44e97befca64050fef4b832e97d4381da7630d8f78.
Historical releases and existing immutable admission checks are unchanged.
Production import capture continues to validate the authored ODO source and
version; no hash-admission waiver or broad fallback is introduced.

The existing 46 CoreDomainAliasValidationTest, WorldviewDeclarationSupportTest
and BroadDescriptionContractTest cases pass with klab.test.core.directory set to
the candidate core directory. They use the existing compiled classes at this
branch's baseline 197a60a37ba3483b7d3f62fa93a510241a22e652. No Java source changes
are required for the additive roots; no clean reactor build is claimed.

IMOD's isolated current-toolchain fixture additionally passes 65 lineage and
nonimplication checks against the exact candidate bytes. Its full-world replay
remains blocked by incompatible/incomplete historical authority captures, so
this change is not certified for full-world activation. IMOD's decision record
2026-11-06-collective-lineage.md gives the validation scope and remaining work.
No push, deployment, restart or original-checkout modification is part of this candidate.
