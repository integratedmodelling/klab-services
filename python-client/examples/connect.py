"""Inspect a deployment or restore/export original work using either provider."""
import argparse
import json

from klab_client import (ConnectionIdentity, DeploymentProfile, HubCertificateProvider,
                        IssuedTokenProvider, connect, load_handle)
from klab_client.errors import InvalidRequestError, KlabError


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--certificate", help="Explicit private ENGINE certificate path")
    group.add_argument("--issued-tokens", action="store_true", help="Read only profile environment references")
    parser.add_argument("--principal", help="Known identity from the trusted issued-credential source")
    parser.add_argument("--issuer")
    parser.add_argument("--handle")
    parser.add_argument("--destination")
    parser.add_argument("--media-type", default="application/geo+json")
    args = parser.parse_args()
    if args.issued_tokens and not args.principal:
        parser.error("Issued tokens require an explicit --principal")
    if bool(args.handle) != bool(args.destination):
        parser.error("Handle export requires both --handle and --destination")
    try:
        profile = DeploymentProfile.load(args.profile)
        provider = (HubCertificateProvider(args.certificate) if args.certificate else
                    IssuedTokenProvider.from_env(profile, ConnectionIdentity(args.principal, args.issuer)))
        with connect(profile, credentials=provider) as connection:
            connection.wait_ready(timeout=30)
            print(json.dumps({"catalog": connection.catalog.summary()}))
            if args.handle:
                handle = load_handle(args.handle)
                if handle.kind != "observation":
                    raise InvalidRequestError("Native export requires an observation handle")
                observation = connection.client.restore_handle(handle)
                result = observation.export_to_file(args.destination, media_type=args.media_type)
                print(json.dumps({"observation_id": observation.id, "bytes": result.bytes_written,
                                  "media_type": result.media_type, "schema_id": result.schema_id}))
        return 0
    except KlabError as error:
        print(type(error).__name__ + ": " + str(error))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
