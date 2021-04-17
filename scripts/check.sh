#!/usr/bin/env bash
# Compile every single-file example the way `java File.java` sees it, then run
# the ones that end with an /* Output: ... */ block and compare what they print.
#
#   scripts/check.sh            # every example
#   scripts/check.sh t-point    # only files under the given paths
#
# JAVA_RELEASE pins `javac --release` (defaults to the running JDK).
# JOBS sets how many files are checked in parallel (defaults to the CPU count).
set -u

cd "$(dirname "$0")/.."
release=${JAVA_RELEASE:-$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2}')}
jobs=${JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)}
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
trap 'exit 130' INT TERM
export release tmp

check_one() {
	f=$1
	work=$(mktemp -d "$tmp/w.XXXXXX")
	echo "$f" >"$work/file"

	# The source launcher ignores the "public class must match the file name"
	# rule, javac does not, so copy the file under its public type's name.
	name=$(grep -m1 -oE '^public ((final|abstract|sealed|non-sealed) )*(class|interface|enum|record) [A-Za-z_][A-Za-z0-9_]*' "$f" | awk '{print $NF}')
	mkdir "$work/src"
	cp "$f" "$work/src/${name:-$(basename "$f" .java)}.java"

	if ! javac --release "$release" -Xlint:none -d "$work/classes" "$work/src/"*.java >"$work/log" 2>&1; then
		echo "compile" >"$work/status"
		echo "FAIL compile  $f"
		return
	fi
	if ! grep -q '^/\* Output:' "$f"; then
		echo "ok" >"$work/status"
		echo "ok            $f"
		return
	fi

	awk '/^\/\* Output:/ {p = 1; next} p && /^\*\// {p = 0} p' "$f" >"$work/expected"
	# 30 s limit per program; perl's alarm stands in for timeout(1) on macOS.
	(cd "$(dirname "$f")" && perl -e 'alarm shift; exec @ARGV' 30 java "$(basename "$f")" >"$work/actual" 2>&1)
	if diff -u "$work/expected" "$work/actual" >"$work/log"; then
		echo "run" >"$work/status"
		echo "ok   output   $f"
	else
		echo "output" >"$work/status"
		echo "FAIL output   $f"
	fi
}
export -f check_one

paths=("$@")
[ ${#paths[@]} -eq 0 ] && paths=(.)
find "${paths[@]}" -name '*.java' -not -path '*/src/main/*' -not -path '*/target/*' -print0 |
	sort -z | xargs -0 -n 1 -P "$jobs" bash -c 'check_one "$1"' _

total=$(find "$tmp" -name status | wc -l | tr -d ' ')
ran=$(grep -lE '^(run|output)$' "$tmp"/w.*/status 2>/dev/null | wc -l | tr -d ' ')
failed=0
for s in "$tmp"/w.*/status; do
	[ -e "$s" ] || continue
	case $(cat "$s") in
	compile | output)
		failed=$((failed + 1))
		w=$(dirname "$s")
		echo
		echo "--- $(cat "$w/file")"
		sed 's/^/    /' "$w/log" | head -20
		;;
	esac
done

echo
echo "java $release: $total compiled, $ran output-checked, $failed failed"
[ "$failed" -eq 0 ]
