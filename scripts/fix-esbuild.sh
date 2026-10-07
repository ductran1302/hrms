#!/usr/bin/env bash
# npm 12 chặn install-scripts của esbuild → approve rồi rebuild.
set -e
cd /workspaces/HRMS/frontend
npm install-scripts approve esbuild 2>&1 | tail -2
npm rebuild esbuild 2>&1 | tail -2
node -e 'console.log("esbuild", require("esbuild").version)'
