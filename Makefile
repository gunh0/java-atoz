JDK ?= 24

.PHONY: check check-docker

# Compile every example with the local JDK and verify /* Output: */ blocks.
check:
	scripts/check.sh

# Same check on a specific JDK release, e.g. `make check-docker JDK=16`.
check-docker:
	docker run --rm -v "$(CURDIR)":/src -w /src eclipse-temurin:$(JDK) scripts/check.sh
