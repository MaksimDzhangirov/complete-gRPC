FROM golang:1.15 AS builder

RUN mkdir -p /go/src/github.com/MaksimDzhangirov/complete-gRPC/code/lecture17

WORKDIR /go/src/github.com/MaksimDzhangirov/complete-gRPC/code/lecture17

COPY go.mod go.sum ./

RUN go mod download

COPY . .

ENV CGO_ENABLED=0 GOOS=linux GOARCH=amd64
RUN go build -ldflags="-s -w" -o grpcserver ./cmd/server