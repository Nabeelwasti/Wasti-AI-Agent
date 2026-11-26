from __future__ import annotations

import threading
import time
from dataclasses import dataclass


@dataclass
class CircuitState:
    consecutive_failures: int = 0
    open_until: float = 0.0
    last_error: str | None = None


class ProviderCircuitBreaker:
    """Process-local circuit breaker; remote health is still verified by real calls."""

    def __init__(self, failure_threshold: int = 2, cooldown_seconds: float = 30.0):
        if failure_threshold < 1 or cooldown_seconds <= 0:
            raise ValueError("Circuit breaker threshold and cooldown must be positive.")
        self.failure_threshold = failure_threshold
        self.cooldown_seconds = cooldown_seconds
        self._states: dict[str, CircuitState] = {}
        self._lock = threading.RLock()

    def available(self, provider_id: str, now: float | None = None) -> bool:
        current = time.monotonic() if now is None else now
        with self._lock:
            state = self._states.get(provider_id)
            if state is None or state.open_until <= current:
                if state is not None and state.open_until:
                    state.open_until = 0.0
                    state.consecutive_failures = 0
                    state.last_error = None
                return True
            return False

    def record_success(self, provider_id: str) -> None:
        with self._lock:
            self._states[provider_id] = CircuitState()

    def record_failure(self, provider_id: str, error: Exception | str, now: float | None = None) -> None:
        current = time.monotonic() if now is None else now
        with self._lock:
            state = self._states.setdefault(provider_id, CircuitState())
            state.consecutive_failures += 1
            state.last_error = str(error)[:240]
            if state.consecutive_failures >= self.failure_threshold:
                state.open_until = current + self.cooldown_seconds

    def snapshot(self, now: float | None = None) -> dict[str, dict[str, object]]:
        current = time.monotonic() if now is None else now
        with self._lock:
            return {
                key: {
                    "consecutive_failures": state.consecutive_failures,
                    "available": state.open_until <= current,
                    "cooldown_remaining_seconds": max(0.0, state.open_until - current),
                    "last_error": state.last_error,
                }
                for key, state in self._states.items()
            }
