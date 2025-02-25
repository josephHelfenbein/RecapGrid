#!/bin/sh

export API_URL=${API_URL:-"http://localhost:8080"}

nginx -g 'daemon off;'
