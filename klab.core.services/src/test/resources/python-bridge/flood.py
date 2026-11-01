import sys

sys.stdin.buffer.read()
for _ in range(256):
    sys.stdout.buffer.write(b"x" * 1024)
    sys.stderr.buffer.write(b"y" * 1024)
