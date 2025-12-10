#!/bin/bash
#
# Copyright © 2016-2025 The LmdbJava Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -euo pipefail

# Pure HTML report generator for LmdbJava version regression testing

# Source common functions
source "$(dirname "$0")/report-common.sh"

DATA_DIR="target/benchmark-cursoriterable"
WORK_DIR="target/benchmark"

# Check prerequisites
JSON_COUNT=$(find "$DATA_DIR" -name "out-*.json" 2>/dev/null | wc -l)
if [ $JSON_COUNT -lt 2 ]; then
  echo "ERROR: Need at least 2 version benchmark result files in $DATA_DIR"
  exit 1
fi

echo "Found ${JSON_COUNT} version benchmark results"

# Get system info
CPU_MODEL=$(get_cpu_info)
CPU_COUNT=$(get_cpu_count)
RAM_GIB=$(get_total_ram_gib)
KERNEL=$(get_kernel)
JAVA_TAG=$(get_java_version)

FIRST_FILE=$(find "$DATA_DIR" \( -name "out-*.json" \) 2>/dev/null | head -1)
BENCH_DATE=$(stat -c %y "$FIRST_FILE" | cut -d' ' -f1)
BENCH_MODE=$(get_benchmark_mode "$FIRST_FILE")

mkdir -p "$WORK_DIR"
cp "$DATA_DIR"/out-*.json "$WORK_DIR/" 2>/dev/null || true

cd "$WORK_DIR"

# List all files
FILES=$(find . -name "*.json" -type f | sort)

## Build version list
#VERSIONS=()
#for f in $FILES; do
#  [ -f "$f" ] || continue
#  VERSION=$(echo "$f" | sed 's/out-\(.*\)\-version-\(.*\)\.json/\2/' | sed 's/out-\(.*\)\-branch-\(.*\)\.json/branch-\2/')
#  VERSIONS+=("$VERSION")
#done

echo "Processing benchmarks and generating chart..."

# Extract data and generate chart
for BENCH in all allBackward atLeast atLeastBackward atMost atMostBackward closed closedBackward closedOpen closedOpenBackward greaterThan greaterThanBackward lessThan lessThanBackward open openBackward openClosed openClosedBackward; do
  > "vers-${BENCH}.dat"
  for FILE in ${FILES}; do
#    if [[ "$VERSION" == branch-* ]]; then
#      FILE="out-${VERSION}.json"
#    else
#      FILE="out-version-${VERSION}.json"
#    fi
    [ -f "$FILE" ] || continue
    VERSION=$(echo "$FILE" | sed 's/\(.*\)\-version-\(.*\)\.json/\2/' | sed 's/\(.*\)\-branch-\(.*\)\.json/branch-\2/')
    SCORE=$(jq -r ".[] | select(.benchmark | contains(\"LmdbJavaCursorIterable.${BENCH}\")) |
      select(.params.intKey == \"true\") | select(.params.sequential == \"true\") |
      .primaryMetric.score" "$FILE" 2>/dev/null || echo "")
    if [ -n "$SCORE" ]; then
      if [[ "$VERSION" == branch-* ]]; then
        BRANCH_PART=$(echo "$VERSION" | sed 's/branch-\([^#]*\)#.*/\1/' | cut -c1-6)
        GIT_PART=$(echo "$VERSION" | sed 's/.*#\(.*\)/\1/')
        VERSION_LABEL="${BRANCH_PART}#${GIT_PART}"
      else
        VERSION_LABEL="$VERSION"
      fi
      echo "\"${VERSION_LABEL}\" ${SCORE}" >> "vers-${BENCH}.dat"
    fi
  done
done

cat > vers-multiplot.gnuplot <<'GNUPLOT'
set terminal svg size 1000,6000 noenhanced
set output 'version-comparison.svg'
set style fill solid 0.25 border
set boxwidth 0.5
set grid y
set multiplot layout 9,2 title "LmdbJava Performance Regression Testing\nMilliseconds per Operation (Smaller is Better)"
set ylabel "ms / operation"
set xlabel ""
set xtics nomirror rotate by -270

set title "all"
plot 'vers-all.dat' using 2:xtic(1) with boxes lc rgb "#F44336" notitle
set title "allBackward"
plot 'vers-allBackward.dat' using 2:xtic(1) with boxes lc rgb "#E91E63" notitle
set title "atLeast"
plot 'vers-atLeast.dat' using 2:xtic(1) with boxes lc rgb "#9C27B0" notitle
set title "atLeastBackward"
plot 'vers-atLeastBackward.dat' using 2:xtic(1) with boxes lc rgb "#673AB7" notitle
set title "atMost"
plot 'vers-atMost.dat' using 2:xtic(1) with boxes lc rgb "#3F51B5" notitle
set title "atMostBackward"
plot 'vers-atMostBackward.dat' using 2:xtic(1) with boxes lc rgb "#2196F3" notitle
set title "closed"
plot 'vers-closed.dat' using 2:xtic(1) with boxes lc rgb "#03A9F4" notitle
set title "closedBackward"
plot 'vers-closedBackward.dat' using 2:xtic(1) with boxes lc rgb "#00BCD4" notitle
set title "closedOpen"
plot 'vers-closedOpen.dat' using 2:xtic(1) with boxes lc rgb "#009688" notitle
set title "closedOpenBackward"
plot 'vers-closedOpenBackward.dat' using 2:xtic(1) with boxes lc rgb "#4CAF50" notitle
set title "greaterThan"
plot 'vers-greaterThan.dat' using 2:xtic(1) with boxes lc rgb "#8BC34A" notitle
set title "greaterThanBackward"
plot 'vers-greaterThanBackward.dat' using 2:xtic(1) with boxes lc rgb "#CDDC39" notitle
set title "lessThan"
plot 'vers-lessThan.dat' using 2:xtic(1) with boxes lc rgb "#FFEB3B" notitle
set title "lessThanBackward"
plot 'vers-lessThanBackward.dat' using 2:xtic(1) with boxes lc rgb "#FFC107" notitle
set title "open"
plot 'vers-open.dat' using 2:xtic(1) with boxes lc rgb "#FF9800" notitle
set title "openBackward"
plot 'vers-openBackward.dat' using 2:xtic(1) with boxes lc rgb "#FF5722" notitle
set title "openClosed"
plot 'vers-openClosed.dat' using 2:xtic(1) with boxes lc rgb "#795548" notitle
set title "openClosedBackward"
plot 'vers-openClosedBackward.dat' using 2:xtic(1) with boxes lc rgb "#607D8B" notitle
unset multiplot
GNUPLOT

gnuplot vers-multiplot.gnuplot
rm -f vers-multiplot.gnuplot vers-*.dat

echo "Generating HTML report..."

# Generate pure HTML report
emit_html_header "LmdbJava Performance Regression Testing" > index.html
echo "  <h1>LmdbJava Performance Regression Testing</h1>" >> index.html
emit_smoketest_warning "$BENCH_MODE" >> index.html

cat >> index.html <<EOHTML

  <figure>
EOHTML

emit_img "version-comparison.svg" "LmdbJava Performance Regression Testing" >> index.html

cat >> index.html <<'EOHTML'
  </figure>

  <h2>Performance Analysis</h2>
  <p>The following tables show each benchmark ranked by performance, with percentage difference from the fastest version.
  <strong>Branch versions</strong> (e.g., <code>master#65df2ee</code>) are highlighted in bold.</p>

EOHTML

# Generate performance tables
declare -A BENCH_NAMES
BENCH_NAMES[all]="all"
BENCH_NAMES[allBackward]="allBackward"
BENCH_NAMES[atLeast]="atLeast"
BENCH_NAMES[atLeastBackward]="atLeastBackward"
BENCH_NAMES[atMost]="atMost"
BENCH_NAMES[atMostBackward]="atMostBackward"
BENCH_NAMES[closed]="closed"
BENCH_NAMES[closedBackward]="closedBackward"
BENCH_NAMES[closedOpen]="closedOpen"
BENCH_NAMES[closedOpenBackward]="closedOpenBackward"
BENCH_NAMES[greaterThan]="greaterThan"
BENCH_NAMES[greaterThanBackward]="greaterThanBackward"
BENCH_NAMES[lessThan]="lessThan"
BENCH_NAMES[lessThanBackward]="lessThanBackward"
BENCH_NAMES[open]="open"
BENCH_NAMES[openBackward]="openBackward"
BENCH_NAMES[openClosed]="openClosed"
BENCH_NAMES[openClosedBackward]="openClosedBackward"

for BENCH in all allBackward atLeast atLeastBackward atMost atMostBackward closed closedBackward closedOpen closedOpenBackward greaterThan greaterThanBackward lessThan lessThanBackward open openBackward openClosed openClosedBackward; do
  echo "  <h3>${BENCH_NAMES[$BENCH]}</h3>" >> index.html
  echo "  <table>" >> index.html
  echo "    <thead><tr><th>Rank</th><th>Version</th><th>ms/op</th><th>vs Fastest</th></tr></thead>" >> index.html
  echo "    <tbody>" >> index.html

  declare -a SCORES
  for FILE in ${FILES}; do
#    if [[ "$VERSION" == branch-* ]]; then
#      FILE="out-${VERSION}.json"
#    else
#      FILE="out-version-${VERSION}.json"
#    fi
    [ -f "$FILE" ] || continue
    SCORE=$(jq -r ".[] | select(.benchmark | contains(\"LmdbJavaCursorIterable.${BENCH}\")) |
      select(.params.intKey == \"true\") | select(.params.sequential == \"true\") |
      .primaryMetric.score" "$FILE" 2>/dev/null || echo "")
    if [ -n "$SCORE" ]; then
      SCORES+=("$SCORE:$VERSION")
    fi
  done

  IFS=$'\n' SORTED=($(sort -t: -k1 -n <<<"${SCORES[*]}"))
  unset IFS
  FASTEST_SCORE=$(echo "${SORTED[0]}" | cut -d: -f1)

  RANK=1
  for ENTRY in "${SORTED[@]}"; do
    SCORE=$(echo "$ENTRY" | cut -d: -f1)
    VERSION=$(echo "$ENTRY" | cut -d: -f2)

    # Format version display
    if [[ "$VERSION" == branch-* ]]; then
      BRANCH_PART=$(echo "$VERSION" | sed 's/branch-\([^#]*\)#.*/\1/' | cut -c1-6)
      GIT_PART=$(echo "$VERSION" | sed 's/.*#\(.*\)/\1/')
      VERSION_DISPLAY="${BRANCH_PART}#${GIT_PART}"
    else
      VERSION_DISPLAY="$VERSION"
    fi

    if [ "$RANK" -eq 1 ]; then
      if [[ "$VERSION" == branch-* ]]; then
        DIFF="<strong>baseline</strong>"
      else
        DIFF="baseline"
      fi
    else
      PERCENT=$(awk -v score="$SCORE" -v fastest="$FASTEST_SCORE" 'BEGIN {printf "%.1f", ((score - fastest) / fastest * 100)}')
      if [[ "$VERSION" == branch-* ]]; then
        DIFF="<strong>+${PERCENT}%</strong>"
      else
        DIFF="+${PERCENT}%"
      fi
    fi

    SCORE_FMT=$(printf "%.3f" "$SCORE")
    echo "      <tr><td>$RANK</td><td><code>$VERSION_DISPLAY</code></td><td>$SCORE_FMT</td><td>$DIFF</td></tr>" >> index.html
    ((RANK++))
  done

  echo "    </tbody>" >> index.html
  echo "  </table>" >> index.html
  unset SCORES
done

cat >> index.html <<EOHTML

  <h2>Tested Versions</h2>
  <ul>
EOHTML

for FILE in ${FILES}; do
  VERSION=$(echo "$FILE" | sed 's/\(.*\)\-version-\(.*\)\.json/\2/' | sed 's/\(.*\)\-branch-\(.*\)\.json/branch-\2/')
  if [[ "$VERSION" == branch-* ]]; then
    BRANCH_NAME=$(echo "$VERSION" | sed 's/branch-\([^#]*\)#.*/\1/')
    GIT_REV=$(echo "$VERSION" | sed 's/.*#\(.*\)/\1/')
    echo "    <li><a href=\"https://github.com/lmdbjava/lmdbjava/tree/${GIT_REV}\"><code>$VERSION</code></a></li>" >> index.html
  else
    echo "    <li><a href=\"https://repo1.maven.org/maven2/org/lmdbjava/lmdbjava/${VERSION}/\"><code>$VERSION</code></a></li>" >> index.html
  fi
done

cat >> index.html <<EOHTML
  </ul>

  <h2>Test Configuration</h2>
  <p>The benchmark was executed on ${BENCH_DATE} using
  <a href="https://github.com/lmdbjava/benchmarks">LmdbJava Benchmarks</a>.</p>

  <p>All tests use the ByteBuffer implementation with the following configuration:</p>

EOHTML

# Emit system environment without LmdbJava version (pass empty strings)
emit_system_environment "$CPU_MODEL" "$CPU_COUNT" "$RAM_GIB" "$KERNEL" "$JAVA_TAG" >> index.html

cat >> index.html <<'EOHTML'

  <h3>Benchmark Configuration</h3>
  <ul>
    <li><strong>Implementation:</strong> ByteBuffer only</li>
    <li><strong>Test Profile:</strong> Run 18 different cursor operations</li>
    <li><strong>Key Type:</strong> Sequential 32-bit integers</li>
    <li><strong>Value Type:</strong> Sequential 32-bit integers</li>
    <li><strong>Access Pattern:</strong> Sequential</li>
    <li><strong>Benchmarks:</strong> All 18 operations (all allBackward atLeast atLeastBackward atMost atMostBackward closed closedBackward closedOpen closedOpenBackward greaterThan greaterThanBackward lessThan lessThanBackward open openBackward openClosed openClosedBackward)</li>
  </ul>
EOHTML

emit_html_footer >> index.html

echo ""
echo "Report generation complete!"
echo "Generated: $WORK_DIR/index.html and $WORK_DIR/version-comparison.svg"
echo ""
echo "Open $WORK_DIR/index.html in your browser to view the report."

cd - > /dev/null
