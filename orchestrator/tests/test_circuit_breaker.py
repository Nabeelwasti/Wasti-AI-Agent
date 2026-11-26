from app.circuit_breaker import ProviderCircuitBreaker


def test_provider_circuit_opens_after_threshold_and_recovers_after_cooldown():
    breaker = ProviderCircuitBreaker(failure_threshold=2, cooldown_seconds=10)
    assert breaker.available("provider-a", now=100)
    breaker.record_failure("provider-a", "timeout", now=100)
    assert breaker.available("provider-a", now=101)
    breaker.record_failure("provider-a", "connection refused", now=101)
    assert not breaker.available("provider-a", now=102)
    assert breaker.snapshot(now=102)["provider-a"]["cooldown_remaining_seconds"] == 9
    assert breaker.available("provider-a", now=112)


def test_success_resets_provider_failures():
    breaker = ProviderCircuitBreaker(failure_threshold=2, cooldown_seconds=10)
    breaker.record_failure("provider-a", "timeout", now=100)
    breaker.record_success("provider-a")
    assert breaker.available("provider-a", now=101)
    assert breaker.snapshot(now=101)["provider-a"]["consecutive_failures"] == 0
