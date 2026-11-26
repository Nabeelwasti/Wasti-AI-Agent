from app.main import endpoint_for, NexusOptions
from pydantic import ValidationError
import pytest

def test_endpoint_for_v1_base():
    assert endpoint_for("http://localhost:8000/v1") == "http://localhost:8000/v1/chat/completions"

def test_endpoint_for_root_base():
    assert endpoint_for("http://localhost:8000") == "http://localhost:8000/v1/chat/completions"

def test_review_rounds_are_bounded():
    assert NexusOptions(review_rounds=2).review_rounds == 2
    with pytest.raises(ValidationError):
        NexusOptions(review_rounds=3)
