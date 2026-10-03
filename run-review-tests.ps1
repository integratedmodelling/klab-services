$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
  & mvn -o -pl klab.services.resources -am test '-Dtest=WorkflowSchemaTest,WorkflowManagerAuthorizationTest,ProposalReviewProtocolTest,ProposalDocumentTest' '-Dsurefire.failIfNoSpecifiedTests=false' "-DargLine=-Duser.home=$($PSScriptRoot.Replace('\','/'))/../test-home" *> ../backend-final-tests.log
  $result = $LASTEXITCODE
} finally { Pop-Location }
exit $result
