# Do not retain original source-file names in the release DEX and let R8
# discard residual source/line information wherever the runtime permits it.
# R8 may still retain minimal register/position records for some methods.
-keepattributes SourceFile
-renamesourcefileattribute ""
