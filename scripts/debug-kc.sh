#!/usr/bin/env bash
echo "== action =="
grep -o 'action="[^"]*"' /tmp/kc-login.html | head -2
echo "== inputs =="
grep -o '<input[^>]*>' /tmp/kc-login.html | head -10
echo "== title =="
grep -o '<title>[^<]*' /tmp/kc-login.html
