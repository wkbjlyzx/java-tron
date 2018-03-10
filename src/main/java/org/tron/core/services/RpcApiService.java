package org.tron.core.services;

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Logger;
import org.tron.api.GrpcAPI;
import org.tron.api.GrpcAPI.AccountList;
import org.tron.api.GrpcAPI.EmptyMessage;
import org.tron.api.GrpcAPI.NodeList;
import org.tron.api.GrpcAPI.WitnessList;
import org.tron.common.application.Application;
import org.tron.common.application.Service;
import org.tron.core.Wallet;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.WitnessCapsule;
import org.tron.core.config.args.Args;
import org.tron.protos.Contract.AccountCreateContract;
import org.tron.protos.Contract.AssetIssueContract;
import org.tron.protos.Contract.TransferContract;
import org.tron.protos.Contract.VoteWitnessContract;
import org.tron.protos.Contract.VoteWitnessContract.Vote;
import org.tron.protos.Contract.WitnessCreateContract;
import org.tron.protos.Protocol.Account;
import org.tron.protos.Protocol.Transaction;

public class RpcApiService implements Service {

  private static final Logger logger = Logger.getLogger(RpcApiService.class.getName());
  private int port = 50051;
  private Server apiServer;
  private Application app;

  public RpcApiService(Application app) {
    this.app = app;
  }

  @Override
  public void init() {

  }

  @Override
  public void init(Args args) {

  }

  @Override
  public void start() {
    try {
      apiServer = ServerBuilder.forPort(port)
          .addService(new WalletApi(app))
          .build()
          .start();
    } catch (IOException e) {
      e.printStackTrace();
    }

    logger.info("Server started, listening on " + port);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.err.println("*** shutting down gRPC server since JVM is shutting down");
      //server.this.stop();
      System.err.println("*** server shut down");
    }));
  }

  private class WalletApi extends org.tron.api.WalletGrpc.WalletImplBase {

    private Application app;
    private Wallet wallet;

    private WalletApi(Application app) {
      this.app = app;
      this.wallet = new Wallet(this.app);
    }


    @Override
    public void getBalance(Account req, StreamObserver<Account> responseObserver) {
      ByteString addressBs = req.getAddress();
      if (addressBs != null) {
        //      byte[] addressBa = addressBs.toByteArray();
        //     long balance = wallet.getBalance(addressBa);
        //    Account reply = Account.newBuilder().setBalance(balance).build();
        Account reply = wallet.getBalance(req);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createTransaction(TransferContract req,
        StreamObserver<Transaction> responseObserver) {
      ByteString fromBs = req.getOwnerAddress();
      ByteString toBs = req.getToAddress();
      long amount = req.getAmount();
      if (fromBs != null && toBs != null && amount > 0) {
        Transaction trx = wallet.createTransaction(req);
        responseObserver.onNext(trx);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void broadcastTransaction(Transaction req,
        StreamObserver<GrpcAPI.Return> responseObserver) {
      boolean ret = wallet.broadcastTransaction(req);
      GrpcAPI.Return retur = GrpcAPI.Return.newBuilder().setResult(ret).build();
      responseObserver.onNext(retur);
      responseObserver.onCompleted();
    }

    @Override
    public void createAccount(AccountCreateContract request,
        StreamObserver<Transaction> responseObserver) {
      if (request.getType() == null || request.getAccountName() == null
          || request.getOwnerAddress() == null) {
        responseObserver.onNext(null);
      } else {
        Transaction trx = wallet.createAccount(request);
        responseObserver.onNext(trx);
      }
      responseObserver.onCompleted();
    }


    @Override
    public void createAssetIssue(AssetIssueContract request,
        StreamObserver<Transaction> responseObserver) {
      ByteString owner = request.getOwnerAddress();
      if (owner != null) {
        Transaction trx = wallet.createTransaction(request);
        responseObserver.onNext(trx);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    //do refactor、test later
    private boolean checkVoteWitnessAccount(VoteWitnessContract req) {

      //send back to cli
      if (req.getOwnerAddress() == null) {
        logger.info("OwnerAddress is null");
        return false;
      }

      AccountCapsule account = app.getDbManager().getAccountStore()
          .get(req.getOwnerAddress().toByteArray());

      if (account == null) {
        logger.info("OwnerAddress[" + req.getOwnerAddress() + "] not exists");
        return false;
      }

      if (req.getVotesCount() <= 0) {
        logger.info("VotesCount[" + req.getVotesCount() + "] <= 0");
        return false;
      }

      if (account.getShare() < req.getVotesCount()) {
        logger.info("Share[" + account.getShare() + "] <  VotesCount[" + req.getVotesCount() + "]");
        return false;
      }

      Iterator<Vote> iterator = req.getVotesList().iterator();
      while (iterator.hasNext()) {
        Vote vote = iterator.next();
        ByteString voteAddress = vote.getVoteAddress();
        WitnessCapsule witness = app.getDbManager().getWitnessStore()
            .get(voteAddress.toByteArray());
        if (witness == null) {
          logger.info("witness[" + voteAddress + "] not exists");
          return false;
        }

        if (vote.getVoteCount() <= 0) {
          logger.info("VoteAddress[" + voteAddress + "],VotesCount[" + vote.getVoteCount()
              + "] <= 0");
          return false;
        }
      }
      return true;
    }

    @Override
    public void voteWitnessAccount(VoteWitnessContract req,
        StreamObserver<Transaction> response) {

      boolean check = checkVoteWitnessAccount(req);//to be complemented later
      if (true) {
        Transaction trx = wallet.createTransaction(req);
        response.onNext(trx);
      } else {
        response.onNext(null);
      }
      response.onCompleted();
    }

    @Override
    public void createWitness(WitnessCreateContract req,
        StreamObserver<Transaction> responseObserver) {
      ByteString fromBs = req.getOwnerAddress();

      if (fromBs != null) {
        Transaction trx = wallet.createTransaction(req);
        responseObserver.onNext(trx);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }


    @Override
    public void listAccounts(EmptyMessage request, StreamObserver<AccountList> responseObserver) {
      responseObserver.onNext(wallet.getAllAccounts());
      responseObserver.onCompleted();
    }

    @Override
    public void listWitnesses(EmptyMessage request, StreamObserver<WitnessList> responseObserver) {
      responseObserver.onNext(wallet.getWitnessList());
      responseObserver.onCompleted();
    }

    @Override
    public void listNodes(EmptyMessage request, StreamObserver<NodeList> responseObserver) {
      // TODO: this.app.getP2pNode().getActiveNodes();
      super.listNodes(request, responseObserver);
    }
  }

  @Override
  public void stop() {

  }

  /**
   * ...
   */
  public void blockUntilShutdown() {
    if (apiServer != null) {
      try {
        apiServer.awaitTermination();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
}
