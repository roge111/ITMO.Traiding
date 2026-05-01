savedcmd_generateQuotes_kernel.mod := printf '%s\n'   generateQuotes_kernel.o | awk '!x[$$0]++ { print("./"$$0) }' > generateQuotes_kernel.mod
