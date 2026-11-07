"""Bounded raw Avro datum codec for the source-defined DataRequest/Instance only.

Not an Avro container or general schema loader. No classes, URLs or schemas are
loaded from a response. Limits are Python policy, not native storage guarantees.
"""
import struct
from typing import Any

from .errors import InvalidRequestError, ProtocolError

MAX_BYTES = 8 * 1024 * 1024
MAX_ITEMS = 262144
MAX_TEXT = 65536
MAX_DEPTH = 16

_NOTIFICATION = ("record", (("message", "string"), ("activityUrn", ("union", ("null", "string"))),
    ("metadata", ("union", ("null", ("map", "string")))), ("code", ("union", ("null", "int"))),
    ("level", ("enum", ("DEBUG", "INFO", "WARNING", "ERROR")))))
_INSTANCE = ("record", (("name", "string"), ("geometry", "string"), ("observable", "string"),
    ("identity", "string"), ("fillingCurve", ("union", ("string", "null"))), ("empty", "boolean"),
    ("notifications", ("array", _NOTIFICATION)), ("metadata", ("map", "string")),
    ("instances", ("union", ("null", ("array", "Instance")))),
    *((name, ("union", ("null", ("array", kind)))) for name, kind in
        (("doubleData", "double"), ("longData", "long"), ("intData", "int"), ("floatData", "float"))),
    ("dataKey", ("union", ("null", ("map", "string"))))))
_REQUEST = ("record", (("observable", "string"), ("geometry", "string"), ("startTime", "long"),
    ("endTime", "long"), ("resourceUrns", ("array", "string")),
    ("inputData", ("union", ("null", "Instance")))))


def _long(value):
    unsigned = (value << 1) ^ (value >> 63)
    result = bytearray()
    while unsigned >= 128:
        result.append((unsigned & 127) | 128)
        unsigned >>= 7
    result.append(unsigned)
    return bytes(result)


def _encode(schema, value):
    output = bytearray(); items = 0
    def emit(raw):
        if len(output) + len(raw) > MAX_BYTES:
            raise ValueError("Provider datum exceeds byte budget")
        output.extend(raw)
    def write(kind, item, depth=0):
        nonlocal items
        if depth > MAX_DEPTH:
            raise ValueError("Provider datum nesting exceeds policy")
        if kind == "Instance": kind = _INSTANCE
        if isinstance(kind, tuple):
            tag, detail = kind
            if tag == "record":
                if not isinstance(item, dict) or set(item) != {key for key, _ in detail}:
                    raise ValueError("Provider record requires exact fields")
                for key, member in detail: write(member, item[key], depth + 1)
            elif tag == "union":
                # Both pinned unions have exactly two branches, one being null.
                index = 0 if (detail[0] == "null") == (item is None) else 1
                emit(_long(index)); write(detail[index], item, depth + 1)
            elif tag == "enum":
                if item not in detail: raise ValueError("Unknown provider enumeration")
                emit(_long(detail.index(item)))
            else:
                if tag == "array" and not isinstance(item, (list, tuple)) or tag == "map" and not isinstance(item, dict):
                    raise ValueError("Provider collection type differs from schema")
                items += len(item)
                if items > MAX_ITEMS: raise ValueError("Provider item budget exceeded")
                if item:
                    emit(_long(len(item)))
                    for key in item:
                        if tag == "map": write("string", key, depth + 1)
                        write(detail, item[key] if tag == "map" else key, depth + 1)
                emit(b"\x00")
        elif kind == "null":
            if item is not None: raise ValueError("Expected null provider value")
        elif kind == "string":
            if not isinstance(item, str): raise ValueError("Expected provider text")
            raw = item.encode("utf-8")
            if len(raw) > MAX_TEXT: raise ValueError("Provider text exceeds policy")
            emit(_long(len(raw))); emit(raw)
        elif kind == "boolean":
            if type(item) is not bool: raise ValueError("Expected provider boolean")
            emit(bytes([item]))
        elif kind in ("long", "int"):
            bits = 64 if kind == "long" else 32
            if type(item) is not int or not -(2**(bits - 1)) <= item < 2**(bits - 1):
                raise ValueError("Provider integer is outside exact schema range")
            emit(_long(item))
        else:
            if type(item) not in (float, int): raise ValueError("Expected provider numeric value")
            emit(struct.pack("<d" if kind == "double" else "<f", item))
    try:
        write(schema, value)
    except (ValueError, UnicodeError, OverflowError, RecursionError, struct.error):
        raise InvalidRequestError("Invalid or over-budget source-defined provider Avro datum") from None
    return bytes(output)


def _decode(schema, raw):
    position = 0; items = 0
    def take(size):
        nonlocal position
        if size < 0 or position + size > len(raw): raise ValueError("Truncated provider datum")
        result = raw[position:position + size]; position += size
        return result
    def number():
        value = 0; index = 0
        while True:
            byte = take(1)[0]
            if index == 9 and byte > 1: raise ValueError("Oversized Avro integer")
            value |= (byte & 127) << (7 * index)
            if not byte & 128: return (value >> 1) ^ -(value & 1)
            index += 1
    def read(kind, depth=0):
        nonlocal items
        if depth > MAX_DEPTH: raise ValueError("Provider nesting budget exceeded")
        if kind == "Instance": kind = _INSTANCE
        if isinstance(kind, tuple):
            tag, detail = kind
            if tag == "record": return {key: read(member, depth + 1) for key, member in detail}
            if tag == "union":
                index = number()
                if not 0 <= index < len(detail): raise ValueError("Invalid provider union branch")
                return read(detail[index], depth + 1)
            if tag == "enum":
                index = number()
                if not 0 <= index < len(detail): raise ValueError("Invalid provider enum index")
                return detail[index]
            result: Any = {} if tag == "map" else []
            count = number()
            while count:
                block_end = None
                if count < 0:
                    count = -count; block_size = number()
                    if block_size < 0 or block_size > len(raw) - position: raise ValueError("Invalid provider block bytes")
                    block_end = position + block_size
                items += count
                if items > MAX_ITEMS: raise ValueError("Provider item budget exceeded")
                for _ in range(count):
                    if tag == "map":
                        key = read("string", depth + 1)
                        if key in result: raise ValueError("Duplicate provider map key")
                        result[key] = read(detail, depth + 1)
                    else: result.append(read(detail, depth + 1))
                if block_end is not None and position != block_end: raise ValueError("Provider block byte count differs")
                count = number()
            return result
        if kind == "null": return None
        if kind == "string":
            size = number()
            if not 0 <= size <= MAX_TEXT: raise ValueError("Provider text budget exceeded")
            return take(size).decode("utf-8")
        if kind == "boolean":
            value = take(1)[0]
            if value not in (0, 1): raise ValueError("Invalid provider boolean")
            return bool(value)
        if kind in ("int", "long"):
            value = number()
            if kind == "int" and not -(2**31) <= value < 2**31: raise ValueError("Provider int overflow")
            return value
        return struct.unpack("<d" if kind == "double" else "<f", take(8 if kind == "double" else 4))[0]
    try:
        if not isinstance(raw, bytes) or len(raw) > MAX_BYTES: raise ValueError("Provider byte budget exceeded")
        value = read(schema)
        if position != len(raw): raise ValueError("Trailing provider bytes")
    except (ValueError, UnicodeError, IndexError, RecursionError, struct.error):
        raise ProtocolError("Invalid or over-budget source-defined provider Avro datum") from None
    return value
