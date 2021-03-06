package org.tron.core.services.http;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.core.Constant;
import org.tron.core.Wallet;
import org.tron.core.capsule.utils.TransactionUtil;
import org.tron.protos.Contract;
import org.tron.protos.Protocol;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;

import static org.tron.core.services.http.Util.getVisible;
import static org.tron.core.services.http.Util.getVisiblePost;
import static org.tron.core.services.http.Util.setTransactionPermissionId;


@Component
@Slf4j(topic = "API")
public class SetAccountIdServlet extends HttpServlet {
    @Autowired
    private Wallet wallet;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) {

    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try {
            String contract = request.getReader().lines()
                    .collect(Collectors.joining(System.lineSeparator()));
            Util.checkBodySize(contract);
            boolean visible = getVisiblePost( contract );
            Contract.SetAccountIdContract.Builder build = Contract.SetAccountIdContract.newBuilder();
            JsonFormat.merge(contract, build, visible );
            Protocol.Transaction tx = wallet.createTransactionCapsule(build.build(),
                            Protocol.Transaction.Contract.ContractType.SetAccountIdContract).getInstance();
            JSONObject jsonObject = JSONObject.parseObject(contract);
            tx = setTransactionPermissionId(jsonObject, tx);
            response.getWriter().println(Util.printCreateTransaction(tx, visible));
        } catch (Exception e) {
            logger.debug("Exception: {}", e.getMessage());
            try {
                response.getWriter().println(Util.printErrorMsg(e));
            } catch (IOException ioe) {
                logger.debug("IOException: {}", ioe.getMessage());
            }
        }
    }
}
