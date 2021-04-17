.PHONY: check

# Compile every example with the local JDK and verify /* Output: */ blocks.
check:
	scripts/check.sh
