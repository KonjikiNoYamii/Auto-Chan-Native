#!/bin/bash
set -e

echo "=========================================="
echo "  Silica Assistant — Test Runner"
echo "=========================================="
echo ""

# Jalankan test
GRADLE_OUTPUT=$(./gradlew :app:testDebugUnitTest 2>&1)
EXIT_CODE=$?
echo "$GRADLE_OUTPUT" | tail -20

REPORT="app/build/reports/tests/testDebugUnitTest/index.html"

echo ""
echo "=========================================="
echo "  HASIL"
echo "=========================================="

if [ -f "$REPORT" ]; then
    # Parse HTML report — ambil angka dari div counter
    TOTAL=$(grep -A1 'id="tests"' "$REPORT" | grep -o '[0-9]*' | head -1)
    FAIL=$(grep -A1 'id="failures"' "$REPORT" | grep -o '[0-9]*' | head -1)
    IGNORE=$(grep -A1 'id="ignored"' "$REPORT" | grep -o '[0-9]*' | head -1)
    PERCENT=$(grep -o '[0-9]*%' "$REPORT" | head -1)
    
    echo "  Total : ${TOTAL:-?}"
    echo "  Gagal : ${FAIL:-0}"
    echo "  Skip  : ${IGNORE:-0}"
    echo "  Rate  : ${PERCENT:-?}"
    echo ""
    
    if [ "$EXIT_CODE" -eq 0 ]; then
        echo "✅ SEMUA TEST PASSING"
    else
        echo "❌ ADA TEST GAGAL"
        # Tampilkan detail dari report class
        CLASSES=$(grep -oP '(?<=href="classes/)[^"]+' "$REPORT" | head -5)
        for cls in $CLASSES; do
            echo "   • $cls"
        done
    fi
    echo ""
    echo "📄 file://$(pwd)/$REPORT"
else
    echo "❌ Gagal — laporan tidak ditemukan"
fi

echo ""
echo "⏱  $(date '+%H:%M:%S') — Selesai"
