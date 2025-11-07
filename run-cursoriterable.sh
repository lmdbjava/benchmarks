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

# Usage: ./run-cursoriterable.sh [smoketest|benchmark [ram_percent]]
# smoketest: Fixed 1K entries for quick verification
# benchmark: Auto-scale entries based on RAM (default 25%, capped at 1M entries)

MODE="${1:-benchmark}"
RAM_PERCENT="${2:-25}"

# Detect total RAM in GB
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
  TOTAL_RAM_KB=$(grep MemTotal /proc/meminfo | awk '{print $2}')
  TOTAL_RAM_GB=$((TOTAL_RAM_KB / 1024 / 1024))
elif [[ "$OSTYPE" == "darwin"* ]]; then
  TOTAL_RAM_BYTES=$(sysctl -n hw.memsize)
  TOTAL_RAM_GB=$((TOTAL_RAM_BYTES / 1024 / 1024 / 1024))
else
  echo "Unsupported OS. Defaulting to 8 GB RAM assumption."
  TOTAL_RAM_GB=8
fi

echo "Detected RAM: ${TOTAL_RAM_GB} GB"

# LmdbJava versions to test
MAVEN_VERSIONS=(0.9.1)
BRANCH_VERSIONS=(pre-gh-273 master gh-249-remove-unsignedkey)

case $MODE in
  smoketest)
    # Fixed small dataset for verification (fast, no warmup)
    ITER_OPTS="-wi 0 -i 1 -f 1"
    R_OPTS="-r 3s"
    NUM_ENTRIES=1000
    echo "Running in SMOKETEST mode (1K entries, fast verification)"
    ;;

  benchmark)
    # Production benchmark with full warmup and iterations
    ITER_OPTS="-wi 3 -i 3 -f 3"
    R_OPTS="-r 120s"

    # Calculate max RAM in bytes (RAM_PERCENT of total)
    MAX_RAM_GB=$((TOTAL_RAM_GB * RAM_PERCENT / 100))
    MAX_RAM_BYTES=$((MAX_RAM_GB * 1024 * 1024 * 1024))

    echo "Max RAM usage: ${MAX_RAM_GB} GB (${RAM_PERCENT}% of ${TOTAL_RAM_GB} GB)"

    # Maximum entry count cap
    MAX_ENTRIES=1000000

    # Calculate entries based on Run 4 config (100 byte values, 4 byte key = 104 byte entries)
    NUM_ENTRIES=$((MAX_RAM_BYTES / 104))
    [ $NUM_ENTRIES -gt $MAX_ENTRIES ] && NUM_ENTRIES=$MAX_ENTRIES

    echo "Calculated entry count: ${NUM_ENTRIES}"
    ;;

  *)
    echo "Usage: $0 [smoketest|benchmark [ram_percent]]"
    echo "  smoketest: Fixed 1K entries for quick verification"
    echo "  benchmark [percent]: Auto-scale entries based on system RAM (default 25%, max 1M entries)"
    echo ""
    echo "Examples:"
    echo "  $0 smoketest          # Fast verification with 1K entries"
    echo "  $0 benchmark          # Use 25% of system RAM (max 1M entries)"
    echo "  $0 benchmark 50       # Use 50% of system RAM (max 1M entries)"
    exit 1
    ;;
esac

# Create output directory (do not delete - allows resumption from failures)
FINAL_OUTPUT_DIR="target/benchmark-cursoriterable"
mkdir -p "$FINAL_OUTPUT_DIR"

# Create temporary directory for results (survives mvn clean)
TEMP_OUTPUT_DIR=$(mktemp -d)
echo "Using temporary directory: ${TEMP_OUTPUT_DIR}"

# JVM flags for Java 9+ module system compatibility
JVM_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-exports java.base/jdk.internal.misc=ALL-UNNAMED --add-exports java.base/sun.nio.ch=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --enable-native-access=ALL-UNNAMED"

# Backup original pom.xml
cp pom.xml pom.xml.backup

echo ""
echo "Testing ${#MAVEN_VERSIONS[@]} Maven versions and ${#BRANCH_VERSIONS[@]} branch versions..."
echo ""

# Output counter variable to keep outputs ordered
OUTPUT_COUNTER=0

# Function to update lmdbjava.version in pom.xml
update_pom_version() {
  local version=$1
  sed -i "s|<lmdbjava.version>.*</lmdbjava.version>|<lmdbjava.version>${version}</lmdbjava.version>|g" pom.xml
}

# Function to run benchmark for Run 4 sequential integer config only
run_benchmark() {
  local output_file=$1
  local version_label=$2

  # Check if benchmark already completed
  if [ -s "${output_file}" ]; then
    return 0
  fi

  echo "  Running benchmark: ${version_label}..."

  # Run all cursor iterable benchmarks
  java $JVM_OPTS -jar target/benchmarks.jar -rf json $ITER_OPTS $R_OPTS -to 60m -tu ms \
    -p num=${NUM_ENTRIES} -p valSize=20 -p sequential=true \
    -rff "${output_file}" \
    LmdbJavaCursorIterable || true

  if [ -f "${output_file}" ]; then
    echo "  ✓ Completed: ${version_label}"
  else
    echo "  ✗ Failed: ${version_label}"
    echo "  Output file expected at: ${output_file}"
    exit 1
  fi
}

# Test Maven versions
for VERSION in "${MAVEN_VERSIONS[@]}"; do
  echo "Testing Maven version: ${VERSION}"

  # Restore original pom.xml
  cp pom.xml.backup pom.xml

  # Update version in pom.xml
  update_pom_version "${VERSION}"

  # Build with new version
  echo "  Building with LmdbJava ${VERSION}..."
  if ! mvn clean package -DskipTests -q; then
    echo "  ✗ Build failed for version ${VERSION}"
    echo "  ERROR: This version may not be compatible with current Java/benchmark code"
    echo "  Cannot continue with remaining tests"
    exit 1
  fi

  # Run benchmark (temp dir survives mvn clean)
  # Increment the output counter
  OUTPUT_COUNTER=$((OUTPUT_COUNTER + 1))
  filename=$(printf "out-%03d-version-%s.json" "$OUTPUT_COUNTER" "$VERSION")
  run_benchmark "${TEMP_OUTPUT_DIR}/${filename}" "version-${VERSION}"

  echo ""
done

# Test branch versions
for BRANCH in "${BRANCH_VERSIONS[@]}"; do
  echo "Testing branch: ${BRANCH}"

  # Restore original pom.xml
  cp pom.xml.backup pom.xml

  # Clone branch into temporary directory
  BRANCH_DIR="target/lmdbjava-src-${BRANCH}"
  rm -rf "${BRANCH_DIR}"

  echo "  Cloning branch ${BRANCH}..."
  if ! git clone --depth 1 --branch "${BRANCH}" https://github.com/lmdbjava/lmdbjava.git "${BRANCH_DIR}" 2>&1 | grep -E "(Cloning|branch)"; then
    echo "  ✗ Failed to clone branch ${BRANCH}"
    echo "  ERROR: Cannot clone branch ${BRANCH} from GitHub"
    echo "  Cannot continue with remaining tests"
    exit 1
  fi

  # Build and install the branch version
  echo "  Building branch ${BRANCH}..."
  cd "${BRANCH_DIR}"

  # Extract version and git revision from branch's pom.xml and git log
  BRANCH_VERSION=$(grep -m 1 "<version>" pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
  GIT_REVISION=$(git rev-parse --short HEAD)
  echo "  Branch version: ${BRANCH_VERSION}"
  echo "  Git revision: ${GIT_REVISION}"

  if ! mvn clean install -DskipTests -Dfmt.skip -q; then
    cd - > /dev/null
    echo "  ✗ Branch build failed for ${BRANCH}"
    echo "  ERROR: Cannot build branch ${BRANCH}"
    echo "  Cannot continue with remaining tests"
    exit 1
  fi

  cd - > /dev/null

  # Update benchmark pom.xml to use branch version
  update_pom_version "${BRANCH_VERSION}"

  # Rebuild benchmark with branch version
  echo "  Building benchmarks with branch ${BRANCH}..."
  if ! mvn clean package -DskipTests -q; then
    echo "  ✗ Benchmark build failed for branch ${BRANCH}"
    echo "  ERROR: Cannot build benchmarks with branch ${BRANCH}"
    echo "  Cannot continue with remaining tests"
    exit 1
  fi

  # Run benchmark (temp dir survives mvn clean)
  # Include git revision in filename for debugging
  # Increment the output counter
  OUTPUT_COUNTER=$((OUTPUT_COUNTER + 1))
  filename=$(printf "out-%03d-branch-%s.json" "$OUTPUT_COUNTER" "$BRANCH")
  run_benchmark "${TEMP_OUTPUT_DIR}/${filename}" "branch-${BRANCH}#${GIT_REVISION}"

  # Cleanup branch directory
  rm -rf "${BRANCH_DIR}"

  echo ""
done

# Restore original pom.xml
cp pom.xml.backup pom.xml
rm pom.xml.backup

# Copy results from temp directory to final location
echo ""
echo "Copying results to ${FINAL_OUTPUT_DIR}..."
mkdir -p "${FINAL_OUTPUT_DIR}"
cp "${TEMP_OUTPUT_DIR}"/out-*.json "${FINAL_OUTPUT_DIR}/" 2>/dev/null || true

# Cleanup temp directory
rm -rf "${TEMP_OUTPUT_DIR}"

echo ""
echo "Version regression testing completed in $MODE mode"
if [ "$MODE" = "benchmark" ]; then
  echo "RAM usage limit: ${RAM_PERCENT}% of ${TOTAL_RAM_GB} GB (max ${NUM_ENTRIES} entries)"
fi

# Count successful results
EXPECTED_COUNT=$((${#MAVEN_VERSIONS[@]} + ${#BRANCH_VERSIONS[@]}))
ACTUAL_COUNT=$(find "${FINAL_OUTPUT_DIR}" -name "out-*.json" 2>/dev/null | wc -l)

echo ""
echo "Results: ${ACTUAL_COUNT} of ${EXPECTED_COUNT} tests completed successfully"
if [ $ACTUAL_COUNT -lt $EXPECTED_COUNT ]; then
  echo "  WARNING: Some tests failed. Expected ${EXPECTED_COUNT} results, got ${ACTUAL_COUNT}"
  exit 1
fi

echo ""
echo "Results available in:"
for file in "$FINAL_OUTPUT_DIR"/*; do
    echo "$file"  # Shows: ./subdirectory/file1.txt
done
#for VERSION in "${MAVEN_VERSIONS[@]}"; do
#  [ -f "${FINAL_OUTPUT_DIR}/out-version-${VERSION}.json" ] && echo "  - ${FINAL_OUTPUT_DIR}/out-version-${VERSION}.json"
#done
#for BRANCH in "${BRANCH_VERSIONS[@]}"; do
#  # Use wildcard to match any git revision
#  for f in "${FINAL_OUTPUT_DIR}"/out-branch-${BRANCH}#*.json; do
#    [ -f "$f" ] && echo "  - $f"
#  done
#done
echo ""
echo "To generate a regression report from these results, run: ./report-cursoriterable.sh"
