import os
import sys

try:
    import ez_setup, Extension
    ez_setup.use_setuptools()
except ImportError:
    pass
from distutils.core import setup, Extension
import subprocess

setup(
    name='goby',
    version='3.3.0',
    packages=['goby'],
    author='Campagne Lab',
    author_email='fac2003@campagnelab.org',
    scripts=[
        'GobyAlignmentStats.py',
        'GobyAlignmentToText.py',
        'GobyCompactToFasta.py',
        'GobyReadsStats.py',
        'SbiPrint.py'
        ''
        ],
    url='http://goby.campagnelab.org/',
    description='Python API for reading read and alignment data files created with the Goby framework.',
    license='GNU LESSER GENERAL PUBLIC LICENSE',
    long_description=open('README.txt').read(),
    # Remove below comments and adjust include path to enable (much) faster protobuf with cpp implementation:
    # when installed this way, do: export PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION=cpp to enable
    #ext_modules=[Extension('gobypodpb',
    #        sources=['cpp/gobypodpb.c','cpp/Alignments.pb.cc',
    #        'cpp/Reads.pb.cc',
    #        'cpp/BaseInformationRecords.pb.cc' ],
    #        include_dirs=['/usr/local/include'], # adjust if protobuf installed elsewhere
    #        library_dirs=['/usr/local/lib'],      # adjust if protobuf installed elsewhere
    #        libraries=['protobuf'])],
    requires=[
        'google.protobuf (>=3.3)',
        ],
    classifiers=[
        'Development Status :: 4 - Beta',
        'Intended Audience :: Developers',
        'Intended Audience :: Science/Research',
        'License :: OSI Approved :: GNU LESSER GENERAL PUBLIC LICENSE',
        'Operating System :: OS Independent',
        'Topic :: Scientific/Engineering :: Bio-Informatics'
        ],
)
