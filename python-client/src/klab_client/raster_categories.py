"""Explicit caller-declared code dictionary over detached numerical raster data.

JAVA-GAP-0014: not native category metadata, inference or committed science.
"""
from dataclasses import dataclass
from typing import Any

from .analysis import RasterAnalysis, DEFAULT_ANALYSIS_BYTES
from .errors import InvalidRequestError, UnsupportedOperationError


@dataclass(frozen=True)
class RasterCategories:
    """Immutable exact integer-code/label pairs and caller attribution, no guessing."""
    entries: tuple
    provenance: str

    def __post_init__(self):
        if (type(self.entries) is not tuple or not 1 <= len(self.entries) <= 256
                or not isinstance(self.provenance, str) or not self.provenance.strip() or len(self.provenance) > 4096):
            raise InvalidRequestError("Category dictionary requires bounded immutable entries and attribution")
        codes, labels = set(), set()
        for entry in self.entries:
            if (type(entry) is not tuple or len(entry) != 2 or type(entry[0]) is not int
                    or not -(2**63) <= entry[0] < 2**63 or not isinstance(entry[1], str)
                    or not entry[1].strip() or len(entry[1]) > 256 or entry[0] in codes or entry[1] in labels):
                raise InvalidRequestError("Category codes and nonblank labels must be unique exact bounded pairs")
            codes.add(entry[0]); labels.add(entry[1])


@dataclass(frozen=True)
class CategoricalRasterAnalysis:
    dataset: object
    dictionary: RasterCategories
    source: RasterAnalysis
    estimated_bytes: int
    semantics: str = "CALLER_DECLARED_CATEGORIES_NOT_NATIVE_CLASSIFICATION"


def categorize_raster(raster, dictionary, *, max_bytes=DEFAULT_ANALYSIS_BYTES):
    """Copy a local integer raster and add exact labels without changing its codes.

    Unknown valid codes refuse. Missingness comes only from the existing mask,
    never from a zero/negative code or an unknown-label coercion. No network I/O.
    """
    if not isinstance(raster, RasterAnalysis) or not isinstance(dictionary, RasterCategories):
        raise InvalidRequestError("Categorical analysis requires an explicit raster and RasterCategories")
    if type(max_bytes) is not int or max_bytes <= 0:
        raise InvalidRequestError("Category allocation budget must be a positive integer")
    import numpy as np
    source_dataset: Any = raster.dataset  # Optional xarray type is loaded only by analysis extra.
    values = source_dataset["value"].values
    valid = source_dataset["valid"].values
    if (values.dtype.kind not in "iu" or valid.dtype.kind != "b" or values.shape != valid.shape
            or source_dataset["value"].dims != source_dataset["valid"].dims):
        raise UnsupportedOperationError("Exact category dictionaries require integer codes and a matching boolean mask")
    if "category" in source_dataset:
        raise UnsupportedOperationError("Existing category data cannot be silently replaced")
    width = max(len(label) for _, label in dictionary.entries)
    # Include retained source, detached dataset/coordinates and simultaneous
    # comparison/mask buffers, not just the final string array.
    estimate = max(raster.descriptor.estimated_bytes, source_dataset.nbytes) + source_dataset.nbytes + values.size * (12 + 4 * width) + 8192
    if estimate > max_bytes:
        raise InvalidRequestError("Category labels and retained source exceed the explicit allocation budget")
    known = np.zeros(values.shape, dtype=bool)
    labels = np.full(values.shape, "", dtype=f"U{width}")
    for code, label in dictionary.entries:
        selected = (values == code) & valid
        labels[selected] = label
        known |= selected
    if np.any(valid & ~known):
        raise UnsupportedOperationError("Valid raster code is absent from the explicit dictionary; no fallback category")
    dataset = source_dataset.copy(deep=True)
    dataset["category"] = (dataset["value"].dims, labels, {
        "category_source": "CALLER_DECLARED", "category_provenance": dictionary.provenance,
        "code_dictionary": dictionary.entries, "missing_label": "", "missingness": "USE_VALID_MASK"})
    return CategoricalRasterAnalysis(dataset, dictionary, raster, estimate)
