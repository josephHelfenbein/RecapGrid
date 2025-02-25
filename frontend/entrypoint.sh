#!/bin/sh
set -e

envsubst '${API_URL}' < /etc/nginx/nginx.conf > /etc/nginx/nginx.conf.tmp
mv /etc/nginx/nginx.conf.tmp /etc/nginx/nginx.conf

exec nginx -g 'daemon off;'