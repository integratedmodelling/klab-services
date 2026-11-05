import pytest

from workflows.async_notebook import check_outputs


def notebook():
    return {"cells": [{"cell_type": "code", "execution_count": number,
        "outputs": [{"output_type": "stream", "text": "controlled output"}]} for number in (1, 2, 3)]}


def test_exact_executed_cells_with_safe_outputs():
    check_outputs(notebook(), ["PRIVATE_CREDENTIAL"])


@pytest.mark.parametrize("defect", ["secret", "jwt", "budget", "order", "error", "count"])
def test_unsafe_unexecuted_or_oversized_notebook_never_passes(defect):
    data = notebook()
    if defect in ("secret", "jwt", "budget"):
        data["cells"][0]["outputs"][0]["text"] = {"secret": "PRIVATE_CREDENTIAL",
            "jwt": "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzY2llbnRpc3QifQ.signature", "budget": "x" * (1024 * 1024)}[defect]
    if defect == "order":
        data["cells"][1]["execution_count"] = None
    if defect == "error":
        data["cells"][1]["outputs"] = [{"output_type": "error"}]
    if defect == "count":
        data["cells"].pop()
    with pytest.raises(AssertionError):
        check_outputs(data, ["PRIVATE_CREDENTIAL"])
