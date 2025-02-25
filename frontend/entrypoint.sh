#!/bin/sh

envsubst '$API_URL' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf

exec "$@"
