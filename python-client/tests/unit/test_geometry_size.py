"""Finite cell counts for direct geometry DTOs, not inferred scientific axes."""
import pytest

from klab_client.api.runtime import GeometryImpl
from klab_client.errors import InvalidRequestError, ProtocolError, UnsupportedOperationError


@pytest.mark.parametrize("shape", [(-2, 3), (True, 3), (1.5, 2), ("2", 3), (None,), None, 3, "23"])
def test_direct_size_refuses_malformed_shape(shape):
    with pytest.raises(InvalidRequestError, match="nonnegative integers"):
        GeometryImpl("space", shape).size()


@pytest.mark.parametrize("shape,expected", [((2, 3), 6), ((0, 3), 0), ((2**40, 2**40), 2**80), ([2, 3], 6)])
def test_direct_finite_size_is_exact_integer(shape, expected):
    result = GeometryImpl("space", shape).size()
    assert type(result) is int and result == expected


def test_scalar_empty_unknown_and_universal_policies_remain_explicit():
    scalar = GeometryImpl()
    assert scalar.dimension() == "scalar" and scalar.is_scalar() and scalar.size() == 1
    assert GeometryImpl("space", (), {"empty": True}).size() == 0
    assert GeometryImpl("universal", (), {"empty": True}).size() == 0
    with pytest.raises(UnsupportedOperationError, match="Universal"):
        GeometryImpl("universal", (2, 3)).size()
    with pytest.raises(UnsupportedOperationError, match="checked finite shape"):
        GeometryImpl("space").size()


@pytest.mark.parametrize("shape", [[-1, 3], [True, 3], [1.5, 3]])
def test_wire_shape_remains_protocol_error_not_direct_request_error(shape):
    with pytest.raises(ProtocolError, match="nonnegative integers"):
        GeometryImpl.from_wire({"dimensions": [{"shape": shape}]})


def test_size_rechecks_mutated_direct_shape_and_wire_finite_dimensions():
    geometry = GeometryImpl("space", (2, 3))
    geometry.shape = (False, 3)
    with pytest.raises(InvalidRequestError): geometry.size()
    decoded = GeometryImpl.from_wire({"dimensions": [{"shape": [2, 3]}]})
    assert decoded.dimension() == "space" and not decoded.is_scalar() and decoded.size() == 6
