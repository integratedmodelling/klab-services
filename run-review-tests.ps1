param([string]$LocalRepository, [string]$ReadOnlyRepository)
$ErrorActionPreference = 'Stop'
$repositoryArgs = @()
if ($LocalRepository) { $repositoryArgs += "-Dmaven.repo.local=$LocalRepository" }
if ($ReadOnlyRepository) { $repositoryArgs += "-Dmaven.repo.local.tail=$ReadOnlyRepository" }
Push-Location $PSScriptRoot
try {
  & mvn -o -pl klab.services.resources -am test @repositoryArgs '-Dtest=WorkflowSchemaTest,WorkflowManagerAuthorizationTest,ProposalReviewProtocolTest,ProposalDocumentTest,IsolatedProposalCandidateValidatorTest,ProposalSchemaValidatorTest,WorldviewValidationTest' '-Dsurefire.failIfNoSpecifiedTests=false' "-DargLine=-Duser.home=$($PSScriptRoot.Replace('\','/'))/../test-home" *> ../backend-final-tests.log
  $result = $LASTEXITCODE
} finally { Pop-Location }
exit $result
