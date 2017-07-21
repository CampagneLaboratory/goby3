import os
import sys
try:
    import ez_setup
    ez_setup.use_setuptools()
except ImportError:
    pass

from distutils.core import setup

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
    requires=[
        'google.protobuf (>=3.0)',
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
