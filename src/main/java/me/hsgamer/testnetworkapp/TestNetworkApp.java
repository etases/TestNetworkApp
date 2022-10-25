package me.hsgamer.testnetworkapp;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.hyperledger.fabric.client.*;
import org.hyperledger.fabric.client.identity.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

public class TestNetworkApp {
    // Path to crypto materials.
    private static final Path cryptoPath = Paths.get("..", "Minifabric", "vars", "keyfiles", "peerOrganizations", "org0.example.com");
    // Path to user certificate.
    private static final Path certPath = cryptoPath.resolve(Paths.get("users", "Admin@org0.example.com", "msp", "signcerts", "Admin@org0.example.com-cert.pem"));
    // Path to user private key directory.
    private static final Path keyDirPath = cryptoPath.resolve(Paths.get("users", "Admin@org0.example.com", "msp", "keystore"));
    // Path to peer tls certificate.
    private static final Path tlsCertPath = cryptoPath.resolve(Paths.get("peers", "peer1.org0.example.com", "tls", "ca.crt"));

    public static void main(String[] args) throws IOException, InvalidKeyException, GatewayException, InterruptedException, CommitException, CertificateException {
        var certReader = Files.newBufferedReader(certPath);
        var certificate = Identities.readX509Certificate(certReader);
        Identity identity = new X509Identity("org0-example-com", certificate);

        Path keyPath;
        try (var keyFiles = Files.list(keyDirPath)) {
            keyPath = keyFiles.findFirst().orElseThrow();
        }
        var keyReader = Files.newBufferedReader(keyPath);
        var privateKey = Identities.readPrivateKey(keyReader);
        Signer signer = Signers.newPrivateKeySigner(privateKey);

        var tlsCertReader = Files.newBufferedReader(tlsCertPath);
        var tlsCert = Identities.readX509Certificate(tlsCertReader);
        SocketAddress endpointAddress = new InetSocketAddress("192.168.1.10", 7002);
        ManagedChannel grpcChannel = NettyChannelBuilder.forAddress(endpointAddress)
                .sslContext(GrpcSslContexts.forClient().trustManager(tlsCert).build()).overrideAuthority("peer1.org0.example.com")
                .build();

        Gateway.Builder builder = Gateway.newInstance()
                .identity(identity)
                .signer(signer)
                .connection(grpcChannel)
                .evaluateOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .endorseOptions(options -> options.withDeadlineAfter(15, TimeUnit.SECONDS))
                .submitOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .commitStatusOptions(options -> options.withDeadlineAfter(1, TimeUnit.MINUTES));

        try (Gateway gateway = builder.connect()) {
            Network network = gateway.getNetwork(args.length > 0 ? args[0] : "mychannel");
            Contract contract = network.getContract("simple");

            var initResult = contract.submitTransaction("invoke", "a", "b", "5");
            System.out.println(new String(initResult, StandardCharsets.UTF_8));

            byte[] queryResult = contract.evaluateTransaction("query", "a");
            System.out.println(new String(queryResult, StandardCharsets.UTF_8));
        } catch (EndorseException e) {
            System.out.println("Endorse error: " + e.getMessage());
            System.out.println("Endorse error details: " + e.getDetails());
        } finally {
            grpcChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
