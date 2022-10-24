package me.hsgamer.testnetworkapp;

import org.hyperledger.fabric.gateway.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;

// TODO: use fabric-gateway
public class TestNetworkApp {
    public static void main(String[] args) throws IOException {
        // Load an existing wallet holding identities used to access the network.
        Path walletDirectory = Paths.get("wallet");
        Wallet wallet = Wallets.newFileSystemWallet(walletDirectory);

        // Path to a common connection profile describing the network.
        Path networkConfigFile = Paths.get("connection.yaml");

        // Configure the gateway connection used to access the network.
        Gateway.Builder builder = Gateway.createBuilder()
                .identity(wallet, "Admin")
                .networkConfig(networkConfigFile);

        // Create a gateway connection
        try (Gateway gateway = builder.connect()) {

            // Obtain a smart contract deployed on the network.
            Network network = gateway.getNetwork(args.length > 0 ? args[0] : "mychannel");
            Contract contract = network.getContract("simple");

            // Submit transactions that store state to the ledger.
            byte[] createCarResult = contract.createTransaction("init").submit("a","200","b","300");
            System.out.println(new String(createCarResult, StandardCharsets.UTF_8));

            // Evaluate transactions that query state from the ledger.
            byte[] queryAllCarsResult = contract.evaluateTransaction("query", "a");
            System.out.println(new String(queryAllCarsResult, StandardCharsets.UTF_8));

        } catch (ContractException | TimeoutException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
