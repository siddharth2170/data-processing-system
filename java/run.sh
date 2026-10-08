#!/usr/bin/env bash
set -euo pipefail
rm -rf out
mkdir -p out
javac -d out src/dataprocessing/*.java
java -cp out dataprocessing.DataProcessingSystem

