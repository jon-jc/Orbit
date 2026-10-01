"""Validate the checked-in contract without starting Orbit or altering data."""

from pathlib import Path

import yaml
from openapi_spec_validator import validate


contract = yaml.safe_load(Path(__file__).with_name("openapi.yaml").read_text(encoding="utf-8"))
validate(contract)
operation_ids = [
    operation["operationId"]
    for methods in contract["paths"].values()
    for method, operation in methods.items()
    if method in {"get", "post", "patch", "put", "delete", "head", "options", "trace"}
]
if len(operation_ids) != len(set(operation_ids)):
    raise ValueError("Each API operation needs a unique operationId.")
print(f"OpenAPI 3.1 valid: {len(contract['paths'])} paths, {len(operation_ids)} operations.")
