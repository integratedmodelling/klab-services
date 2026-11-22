---
sessionId: session-261122-103458-109h
---

# Commit Message

Add redacted message view to administrative diagnostic logs

- Add `includeMessages` flag to `LogQuery` enabling opt-in redacted message text alongside
  the existing vetted diagnostics view
- Add `LogMessageRedactor` with pattern-based redaction for credential fields, authorization
  values, common token formats, email addresses, URL user information, queries, fragments,
  XML secrets, and private-key blocks, withholding truncated headers and unvetted continuations
- Thread `currentSmtpPassword` supplier through `DiagnosticLogReader` so the configured SMTP
  password is masked in message text; advertise `redactedMessages` capability in `LogCapabilities`
- Update `AdminLogController` to pass the current SMTP password to the reader on construction
- Update `ADMIN_LOGS.md` documenting the message view policy, redaction behavior, and security
  boundaries including the limitation that unlabelled secrets in unknown formats can escape
- Add `messageViewRetainsTroubleshootingTextAndRedactsSecretsAcrossPages` and
  `messageViewWithholdsTruncatedHeadersAndNeverReadsUnsupportedContinuations` tests covering
  redaction patterns, cursor mode retention, and multi-page behavior
- Update `AdminLogSecurityTest` to verify message redaction in query responses with SMTP
  password masking and `no-store` cache control
- Update `ServiceLogs` Vue component and service administration Playwright test for message
  view query and redacted output display

# Delivery Steps

###   Step 1: Commit message ready
The commit message is prepared and ready for use.