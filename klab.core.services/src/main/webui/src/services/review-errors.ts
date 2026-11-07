/** Presentation only: server authorization and revision checks remain authoritative. */
export function reviewError(cause: unknown): string {
  const detail = cause instanceof Error ? cause.message : String(cause);
  if (/^409\b/.test(detail)) return "This review changed while you were working. Your unsaved text is retained. Save or download it, then refresh before explicitly retrying. (409 Conflict)";
  if (/^401\b/.test(detail)) return "Your session has expired. Keep a copy of unsaved work and sign in again. (401)";
  if (/^403\b/.test(detail)) return "You no longer have access to this review. Your unsaved feedback is retained for copying or download. (403 Forbidden)";
  if (/^404\b/.test(detail)) return "This review or its material is no longer available. Check the selected review and refresh. (404)";
  if (/^429\b/.test(detail)) return "The service is receiving too many requests. Keep your work and try again shortly. (429)";
  if (/^5\d\d\b/.test(detail) || /fetch|network|timeout|offline/i.test(detail)) return "The service could not confirm the operation. Keep your work and refresh to check what was saved before trying again.";
  return detail;
}
