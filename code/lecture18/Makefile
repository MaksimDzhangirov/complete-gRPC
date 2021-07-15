gen:
	protoc --proto_path=proto --go_out=pb --go-grpc_out=pb --grpc-gateway_out ./pb --openapiv2_out ./openapiv2  proto/*.proto
clean:
	rm pb/*.go
server:
	go run cmd/server/main.go -port 8080
rest:
	go run cmd/server/main.go -port 8081 -type rest -endpoint 0.0.0.0:8080
client:
	go run cmd/client/main.go -address 0.0.0.0:8080
client-tls:
	go run cmd/client/main.go -address 0.0.0.0:8080 -tls
test:
	go test -cover -race ./...
cert:
	cd cert; ./gen.sh; cd ..

.PHONY: gen clean server client test cert

