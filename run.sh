#!/bin/bash
# ─────────────────────────────────────────────────────────
#  Lexora v1.0 — Build & Run Script
#  Requirements: Java 17+ (JDK)
#  Install JDK: sudo apt install default-jdk   (Linux)
#               brew install openjdk@21         (Mac)
# ─────────────────────────────────────────────────────────

set -e

echo "🔨 Building Lexora v1.0..."
mkdir -p out

# Compile all Java files
javac -d out -sourcepath src $(find src -name "*.java")

echo "✅ Build successful!"
echo ""
echo "🚀 Starting Lexora..."
echo ""

# Run — must be executed from project root so data/ folder is relative
java -cp out lexora.Main
