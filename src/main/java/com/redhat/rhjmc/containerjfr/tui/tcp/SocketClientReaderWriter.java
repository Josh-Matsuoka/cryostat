package com.redhat.rhjmc.containerjfr.tui.tcp;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;
import java.util.concurrent.Semaphore;

import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.core.util.log.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;

class SocketClientReaderWriter implements ClientReader, ClientWriter {

    private final Logger logger;
    private final Thread listenerThread;
    private final ServerSocket ss;
    private final Semaphore semaphore = new Semaphore(0, true);
    private volatile Socket s;
    private volatile Scanner scanner;
    private volatile OutputStreamWriter writer;

    SocketClientReaderWriter(Logger logger, int port) throws IOException {
        this.logger = logger;
        ss = new ServerSocket(port);
        listenerThread = new Thread(() -> {
            System.out.println(String.format("Listening on port %d", port));
            while (true) {
                try {
                    Socket sock = ss.accept();
                    try {
                        close();
                    } catch (IOException e) {
                        logger.warn(ExceptionUtils.getStackTrace(e));
                    }
                    System.out.println(String.format("Connected: %s", sock.getRemoteSocketAddress().toString()));
                    try {
                        s = sock;
                        scanner = new Scanner(sock.getInputStream(), "utf-8");
                        writer = new OutputStreamWriter(sock.getOutputStream(), "utf-8");
                    } finally {
                        semaphore.release();
                    }
                } catch (SocketException e) {
                    semaphore.drainPermits();
                } catch (IOException e) {
                    logger.warn(ExceptionUtils.getStackTrace(e));
                }
            }
        });
        listenerThread.start();
    }

    @Override
    public void close() throws IOException {
        semaphore.drainPermits();
        if (scanner != null) {
            scanner.close();
        }
        if (writer != null) {
            writer.close();
        }
        if (s != null) {
            s.close();
        }
    }

    @Override
    public String readLine() {
        try {
            semaphore.acquire();
            try {
                return scanner.nextLine();
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException e) {
            logger.warn(ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    @Override
    public void print(String s) {
        try {
            semaphore.acquire();
            try {
                writer.write(s);
                writer.flush();
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException | IOException e) {
            logger.warn(ExceptionUtils.getStackTrace(e));
        }
    }

}
