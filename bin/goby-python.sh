#!/usr/bin/env bash
export PYTHONPATH=${GOBY_DIR}/python:${PYTHONPATH}

python "$@"
