import sys

# Echo arbitrary test data as the response, with a successful exit status.
sys.stdout.buffer.write(sys.stdin.buffer.read())
