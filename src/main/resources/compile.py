import py_compile
import sys

source_file = sys.argv[1]
target_file = sys.argv[2]

py_compile.compile(source_file, cfile=target_file)
