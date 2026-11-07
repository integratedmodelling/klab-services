"""Impossible local delivery state fails closed; mocked broker, not native acceptance."""
import pytest

from klab_client.errors import TransportError
from test_agent_messaging import environment, make


def test_completed_local_drive_without_delivery_disconnects_without_ack(environment, monkeypatch):
    channel = make(environment)
    with pytest.raises(TransportError, match='cleanup unconfirmed'):
        with channel:
            with monkeypatch.context() as patch:
                patch.setattr(channel, '_drive', lambda *args, **kwargs: None)
                with pytest.raises(TransportError):
                    channel.read(binding=environment[0])
            assert environment[3][0].channel_object.acks == []
            assert channel.state == 'DISCONNECTED'
    assert len(environment[3]) == 1
    assert channel.local_cleanup_confirmed is False
