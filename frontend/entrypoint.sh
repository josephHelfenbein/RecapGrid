#!/bin/sh

export API_URL="${API_URL:-http://localhost:8080}"

pnpm start &

nginx -g 'daemon off;'
