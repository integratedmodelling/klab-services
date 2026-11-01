import sys

sys.stdin.buffer.read()
sys.stderr.buffer.write("fixture failure: calcul échoué\n".encode("utf-8"))
sys.exit(7)
