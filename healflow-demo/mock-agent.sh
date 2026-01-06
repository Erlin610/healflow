#!/bin/bash

set -u

echo "AI: Analyzing project structure..."

echo "AI: I need to read 'pom.xml'. Allow? [y/n]"
response=""
IFS= read -r response || true
response=${response%$'\r'}

if [[ "$response" == "y" || "$response" == "yes" ]]; then
  echo "AI: Permission granted. Reading file..."
else
  echo "AI: Permission denied. Aborting."
  exit 1
fi

echo "AI: I found a bug. I want to create 'Fix.java'. Allow write? [y/n]"
response2=""
IFS= read -r response2 || true
response2=${response2%$'\r'}

if [[ "$response2" == "y" || "$response2" == "yes" ]]; then
  echo "AI: Fix applied successfully."
  workspace_dir="${HF_WORKSPACE_DIR:-/src}"
  if [[ -d "$workspace_dir" ]]; then
    echo "public class Fix {}" > "${workspace_dir%/}/Fix.java"
  else
    echo "public class Fix {}" > "Fix.java"
  fi
else
  echo "AI: Write denied."
  exit 1
fi
