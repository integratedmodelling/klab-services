"""Client failures, separate from a successful HTTP response."""


class KlabError(Exception):
    """Base error; diagnostics are sanitized by the transport."""


class ConfigurationError(KlabError):
    pass


class TransportError(KlabError):
    pass


class SubmissionOutcomeUnknown(TransportError):
    """The request may have reached the server. Do not blindly resubmit."""


class AuthenticationError(KlabError):
    pass


class AuthorizationError(KlabError):
    pass


class InvalidRequestError(KlabError):
    pass


class MissingAssetError(KlabError):
    pass


class ProtocolError(KlabError):
    pass


class ServerError(KlabError):
    pass


class JobFailedError(KlabError):
    def __init__(self, job_id: int, message: str):
        self.job_id = job_id
        super().__init__(f"Job {job_id}: {message}")


class JobCancelledError(JobFailedError):
    pass


class JobUnavailableError(JobFailedError):
    pass


class WaitTimeout(KlabError, TimeoutError):
    def __init__(self, job):
        self.job = job
        super().__init__(f"Stopped waiting for job {job.id}; computation may still be running")


class UnsupportedOperationError(KlabError, NotImplementedError):
    pass
