FROM nginx

RUN mkdir -p /etc/nginx/cert

COPY ./cert/server-cert.pem /etc/nginx/cert
COPY ./cert/server-key.pem  /etc/nginx/cert
COPY ./cert/ca-cert.pem     /etc/nginx/cert

COPY ./nginx/config/nginx.conf /etc/nginx/nginx.conf