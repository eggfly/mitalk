package main

import (
	"bytes"
	"encoding/hex"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
)

var localAddr *string = flag.String("l", "localhost:9999", "local address")
var remoteAddr *string = flag.String("r", "localhost:80", "remote address")

func proxyConn(conn *net.TCPConn) error {
	rAddr, err := net.ResolveTCPAddr("tcp", *remoteAddr)
	if err != nil {
		panic(err)
	}

	rConn, err := net.DialTCP("tcp", nil, rAddr)
	if err != nil {
		return err
	}
	defer rConn.Close()

	buf := &bytes.Buffer{}
	for {
		data := make([]byte, 256)
		n, err := conn.Read(data)
		if err != nil {
			return err
		}
		buf.Write(data[:n])
		if data[0] == 13 && data[1] == 10 {
			break
		}
	}

	if _, err := rConn.Write(buf.Bytes()); err != nil {
		return err
	}
	log.Printf("sent:\n%v", hex.Dump(buf.Bytes()))

	data := make([]byte, 1024)
	n, err := rConn.Read(data)
	if err != nil {
		if err != io.EOF {
			return err
		} else {
			log.Printf("received err: %v", err)
		}
	}
	log.Printf("received:\n%v", hex.Dump(data[:n]))
	return nil
}

func handleConn(in <-chan *net.TCPConn, out chan<- *net.TCPConn) {
	for conn := range in {
		err := proxyConn(conn)
		log.Printf("proxyConn error: %v", err)
		out <- conn
	}
}

func closeConn(in <-chan *net.TCPConn) {
	for conn := range in {
		conn.Close()
	}
}

func main() {
	flag.Parse()

	fmt.Printf("Listening: %v\nProxying: %v\n\n", *localAddr, *remoteAddr)

	addr, err := net.ResolveTCPAddr("tcp", *localAddr)
	if err != nil {
		panic(err)
	}

	listener, err := net.ListenTCP("tcp", addr)
	if err != nil {
		panic(err)
	}

	pending, complete := make(chan *net.TCPConn), make(chan *net.TCPConn)

	for i := 0; i < 5; i++ {
		go handleConn(pending, complete)
	}
	go closeConn(complete)

	for {
		conn, err := listener.AcceptTCP()
		if err != nil {
			panic(err)
		}
		pending <- conn
	}
}
