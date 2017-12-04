#!/usr/bin/env bash
if [ "$#" -lt 1 ]; then
    echo "Need at least one parameter, path to python file rooted at GOBY_DIR/python; arguments after should be arguments to script"
    exit 1
fi
export PYTHONPATH=${GOBY_DIR}/python:${PYTHONPATH}
python_script=${GOBY_DIR}/python/"$1"
shift
python3 "${python_script}" "$@"