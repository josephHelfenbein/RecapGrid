#!/bin/sh
set -e

envsubst '$API_URL $PORT' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf

nuxt start -p 8080 &

exec nginx -g 'daemon off;'
