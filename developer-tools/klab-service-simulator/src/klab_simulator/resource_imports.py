"""Finite fixture import protocol, not a raster adapter or Java admission engine.

Native multipart/schema/ResourceSet shapes come from Resources imports and the
SDK's existing socket contracts. Private catalog materialization, cleanup and hash
metadata are PROVISIONAL_SIMULATOR_CONTRACT. Only the pinned fixture is admitted.
"""
from email.parser import BytesParser
from email.policy import default
import hashlib

from .fixtures import CROP_SHA
from .state import ContractError

SCHEMA = "raster.geotiff.import"
MEDIA = "image/tiff;application=geotiff"


def schemas():
    return {"raster": [{"schemaId": SCHEMA, "knowledgeClass": "RESOURCE", "type": "STREAM",
                         "mediaTypes": [MEDIA], "description": "Pinned fixture import; simulated admission only"}]}


def parse_upload(headers, body):
    content_type = headers.get("Content-Type", "")
    if not content_type.startswith("multipart/form-data;") or len(content_type) > 512:
        raise ContractError(415, "A bounded multipart file envelope is required")
    message = BytesParser(policy=default).parsebytes(("Content-Type: " + content_type + "\r\n\r\n").encode() + body)
    parts = list(message.iter_parts())
    if message.defects or len(parts) != 1 or (message.epilogue or "").strip() or (message.preamble or "").strip():
        raise ContractError(400, "Exactly one multipart file is supported")
    part = parts[0]
    if (part.defects or part.is_multipart() or part.get_content_disposition() != "form-data"
            or part.get_param("name", header="content-disposition") != "file"
            or not part.get_filename() or part.get_content_type() != "image/tiff"
            or part.get_param("application") != "geotiff"):
        raise ContractError(422, "The declared file field/media type does not match the fixture importer")
    content = part.get_payload(decode=True)
    if not isinstance(content, bytes) or not content or hashlib.sha256(content).hexdigest() != CROP_SHA:
        raise ContractError(422, "Finite simulator importer accepts only the pinned native fixture; no arbitrary raster admission")
    return {"sourceSha256": CROP_SHA, "sourceBytes": len(content)}
