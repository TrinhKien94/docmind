"""Engine adapters: pluggable parsers behind a common interface.

Real ML libraries are lazy-imported inside each engine's ``parse()`` so the
service boots and tests run on CPU with no models installed.
"""
