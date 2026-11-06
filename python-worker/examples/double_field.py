"""Trusted synthetic example, not a port of a k.LAB scientific algorithm."""
import json
import sys

field = json.load(sys.stdin)
field["values"] = [2 * value if valid else None for value, valid in zip(field["values"], field["valid"])]
json.dump(field, sys.stdout, allow_nan=False)
