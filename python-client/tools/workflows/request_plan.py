"""Select successful recorded plan models; never infer execution from a request DTO."""


def model_urns(evidence):
    return sorted({node.raw["metadata"]["urn"]
        for activity in evidence.activities if activity.outcome == "SUCCESS"
        for chart in activity.charts if chart.phase == "resolution plan"
        for node in chart.nodes if node.raw.get("metadata", {}).get("kind") == "model"})
