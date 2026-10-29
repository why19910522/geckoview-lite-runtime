#!/usr/bin/env python3
"""Verify a probe host through an explicitly forwarded local Gecko DevTools socket."""
import argparse
import json
from pathlib import Path
import socket

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--port', type=int, required=True)
p.add_argument('--output', type=Path, required=True)
p.add_argument('--navigate-pdf', action='store_true', help='After checks, navigate to the local PDF fallback fixture')
a = p.parse_args()
with socket.create_connection(('127.0.0.1', a.port), timeout=20) as sock:
    stream = sock.makefile('rb')
    def receive():
        length = b''
        while True:
            char = stream.read(1)
            if char == b':':
                break
            if not char:
                raise EOFError('DevTools connection closed')
            length += char
        size = int(length)
        data = stream.read(size)
        assert len(data) == size
        return json.loads(data)
    def send(value):
        data = json.dumps(value).encode()
        sock.sendall(str(len(data)).encode() + b':' + data)
    def request(actor, kind, **arguments):
        send({'to': actor, 'type': kind, **arguments})
        while True:
            response = receive()
            if response.get('from') == actor:
                assert 'error' not in response, response
                return response
    greeting = receive()
    assert greeting['applicationType'] == 'browser'
    tabs = request('root', 'listTabs')['tabs']
    matches = [tab for tab in tabs if tab.get('url', '').startswith('resource://android/assets/runtime-probe.html?')]
    assert len(matches) == 1, 'Expected exactly one local runtime probe page'
    frame = request(matches[0]['actor'], 'getTarget')['frame']
    assert frame.get('threadActor') and frame.get('inspectorActor')
    acknowledgment = request(frame['consoleActor'], 'evaluateJSAsync', text='JSON.stringify(window.runtimeProbeResult)', disableBreaks=True)
    while True:
        result = receive()
        if result.get('type') == 'evaluationResult' and result.get('resultID') == acknowledgment['resultID']:
            break
    assert not result.get('hasException'), result
    probe = json.loads(result['result'])
    assert probe['passed'] is True, probe
    report = {'devtools_handshake': True, 'list_tabs': True, 'target': True, 'console_evaluation': True, 'thread_and_inspector_actors': True, 'runtime_probe': probe}
    a.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(report, ensure_ascii=False))
    if a.navigate_pdf:
        acknowledgment = request(frame['consoleActor'], 'evaluateJSAsync', text="setTimeout(() => { location.href = 'resource://android/assets/runtime-probe.pdf'; }, 100); 'scheduled'", disableBreaks=True)
        while True:
            result = receive()
            if result.get('type') == 'evaluationResult' and result.get('resultID') == acknowledgment['resultID']:
                assert not result.get('hasException') and result['result'] == 'scheduled'
                break
        print('Local PDF fallback navigation scheduled; verify the host external-response callback separately.')
